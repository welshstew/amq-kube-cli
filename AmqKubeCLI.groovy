 import de.vandermeer.asciitable.v2.RenderedTable
import de.vandermeer.asciitable.v2.V2_AsciiTable
import de.vandermeer.asciitable.v2.render.V2_AsciiTableRenderer
import de.vandermeer.asciitable.v2.render.WidthLongestWord
import de.vandermeer.asciitable.v2.themes.V2_E_TableThemes
import groovy.json.JsonOutput
import groovyx.net.http.HTTPBuilder
import io.fabric8.kubernetes.api.model.PodList
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.impl.SimpleRegistry
import org.apache.camel.processor.aggregate.AggregationStrategy
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Created by swinchester on 14/09/16.
 */
@GrabResolver(name='fusesource.m2', root='https://repo.fusesource.com/nexus/content/groups/public')
@GrabResolver(name='fusesource.ea', root='https://repo.fusesource.com/nexus/content/groups/ea')
@GrabResolver(name='redhat.ga', root='https://maven.repository.redhat.com/ga')
// @Grab(group='io.fabric8', module='kubernetes-client', version='1.3.26.redhat-079')
// @Grab(group='io.fabric8', module='kubernetes-api', version='2.2.0.redhat-079')
// @Grab(group='io.fabric8', module='kubernetes-model', version='1.0.22.redhat-079')

@Grab(group='io.fabric8', module='kubernetes-client', version='3.1.4.fuse-720004-redhat-00001')
@Grab(group='io.fabric8', module='kubernetes-api', version='3.0.11.fuse-720027-redhat-00001')
@Grab(group='io.fabric8', module='kubernetes-model', version='2.0.10.fuse-720030-redhat-00001')

@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1')
@Grab(group='org.apache.camel', module='camel-core', version='2.17.3')
@Grab(group='org.apache.camel', module='camel-stream', version='2.17.3')
@Grab(group='log4j', module='log4j', version='1.2.17')
@Grab(group='org.slf4j', module='slf4j-api', version='1.7.7')
@Grab(group='org.slf4j', module='slf4j-log4j12', version='1.7.7')
@Grab(group='de.vandermeer', module = 'asciitable', version = '0.2.5')


def static setupCamel() {

    def camelCtx = new DefaultCamelContext(new SimpleRegistry())

    camelCtx.addRoutes(new RouteBuilder() {
        def void configure() {
            from('stream:in?promptMessage=AMQ Operation: ')
                    .process {
                def commands = it.in.getBody(String.class).tokenize(' ')
                commands.eachWithIndex { String entry, int i ->
                    if(i == 0) {it.in.headers.put('route', "direct:${entry}")
                    } else { it.in.headers.put('param' + i , entry) }
                }
            }
            .recipientList(header('route'))

            from('direct:qs').to('direct:queuesize')

            from('direct:queuesize')
                .setBody(constant([type:"read",
                               mbean:"org.apache.activemq:type=Broker,brokerName=kube-lookup,destinationType=Queue,destinationName=*",
                               attribute: ["QueueSize"]]))
                .to('direct:aggregate')
                .to('direct:generateTable')

            from('direct:queuepurge')
                .process( { it.in.body = [type:"EXEC",
                                          mbean:"org.apache.activemq:type=Broker,brokerName=kube-lookup,destinationType=Queue,destinationName=${it.in.headers.'param1'}",
                                          operation: "purge"]} )
                .to('direct:aggregate')
                .process({ println "OK!" })

            from('direct:aggregate')
                .setProperty('originalBody', simple('${body}'))
                .process(new Processor() {
                @Override
                void process(Exchange exchange) throws Exception {
                    def client = new DefaultKubernetesClient()
                    def namespace = System.properties.'kubernetes.namespace'
                    def labelName = System.properties.'kubernetes.labelname'
                    def labelValue = System.properties.'kubernetes.labelvalue'
                    PodList kubePods = client.inNamespace(namespace).pods().withLabel(labelName,labelValue).list()
                    def podNames = kubePods.items.collect { it.metadata.name }
                    exchange.in.headers.put("podNames", podNames)
                }
            }).split(header('podNames'), new JsonAggregationStrategy()).parallelProcessing()
                .process(new JolokiaRequestProcessor())
            .end()

            from('direct:generateTable')
            .process(new JolokiaResponseToSystemOutTable())

        }
    })

    camelCtx.start()
    // Stop Camel when the JVM is shut down
    Runtime.runtime.addShutdownHook({ ->
        camelCtx.stop()
    })
    synchronized(this){ this.wait() }
}

def class JsonAggregationStrategy implements AggregationStrategy{
    Logger log = LoggerFactory.getLogger(this.class)
    @Override
    Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        if(oldExchange == null){
            def bodyList = []
            bodyList << newExchange.in.body
            newExchange.in.body = bodyList
            return newExchange
        }else{
            oldExchange.in.body << newExchange.in.body
            return oldExchange
        }
    }
}

def class JolokiaRequestProcessor implements Processor{

    Logger log = LoggerFactory.getLogger(JolokiaRequestProcessor.class)

    @Override
    void process(Exchange exchange) throws Exception {
        def namespace = System.properties.'kubernetes.namespace'
        def http = new HTTPBuilder(System.properties.'kubernetes.master')
        http.ignoreSSLIssues()
        def broker = exchange.in.body
        def uriPath = "/api/v1/namespaces/${namespace}/pods/https:${broker}:8778/proxy/jolokia/?maxDepth=7&maxCollectionSize=500&ignoreErrors=true&canonicalNaming=false"
        def postBody = [:]
        postBody << exchange.properties['originalBody']
        def mbean = (postBody['mbean'] as String).replaceAll("kube-lookup",broker)
        postBody['mbean'] = mbean
        http.request( groovyx.net.http.Method.POST, groovyx.net.http.ContentType.JSON) {

            uri.path = uriPath
            body =  JsonOutput.toJson(postBody)
            headers = [Authorization: "Bearer ${System.properties.'kubernetes.auth.token'}",
                       Accept: groovyx.net.http.ContentType.JSON]

            response.success = { resp, json ->
                json << [broker:broker]
                exchange.in.body = json
            }
            response.failure = { resp ->
                log.debug(resp.data)
            }
        }
    }
}

def class JolokiaResponseToSystemOutTable implements Processor {
    @Override
    void process(Exchange exchange) throws Exception {
        def listy = jolokiaResponseConverter(exchange.in.body)
        generate(listy)
    }

    def generate(List listOfMaps){

        V2_AsciiTable at = new V2_AsciiTable();
        List columns = new ArrayList<String>(listOfMaps[0].keySet())
        at.addRule();
        at.addRow(columns.toArray());
        at.addRule();

        listOfMaps.each { it ->
            Map thing = it
            at.addRow(thing.values().asList().toArray());
            at.addRule()
        }

        V2_AsciiTableRenderer rend = new V2_AsciiTableRenderer();
        rend.setTheme(V2_E_TableThemes.UTF_LIGHT.get());
        rend.setWidth(new WidthLongestWord());

        RenderedTable rt = rend.render(at);
        println rt
    }

    def List jolokiaResponseConverter(ArrayList inputList){
        //println JsonOutput.toJson(inputList)
        def queueValueList = []
        inputList.each { it1 ->
            def brokerName = it1.broker
            it1.value.each { it2 ->
                def valueMap = ['broker':brokerName]
                valueMap.put('mbean', it2.key)
                it2.value.each{ it3 ->
                    valueMap.put(it3.key, it3.value)
                }
                queueValueList.add(valueMap)
            }
        }
        return queueValueList
    }
}

def ipaddr = "minishift ip".execute().text.trim()
def authToken = "oc whoami -t".execute().text.trim()


println ipaddr
println authToken

System.setProperty("kubernetes.master", "https://$ipaddr:8443")
System.setProperty("kubernetes.namespace", "myproject")
System.setProperty("kubernetes.labelname", "deploymentconfig")
System.setProperty("kubernetes.labelvalue", "broker-amq")
System.setProperty("kubernetes.auth.token", authToken)
System.setProperty("kubernetes.trust.certificates", "true")

setupCamel()