# amq-kube-cli

This longish groovy script came about as a way to call multiple jolokia endpoints in order to show the results.  This can be more difficult when you have a number of ActiveMQ pods running in OpenShift but need one view of the data.

Currently the script allows you to get back queue information on queuesize, and allows the purging of a queue (the purge will happen across all the pods matching the kube selector).

#### How does it do it?

1.  Builds up a jolokia request (with some placeholders)
2.  Calls the Kubernetes API to discover pods - based on the labelname and labelvalue
3.  For each of the pods found the jolokia request is forwarded to those pods
4.  The results are aggregated
5.  In the case of the queuesize command - the results are tabulated and shown on-screen

#### What else could I make it do?

WHatever else is available via the jolokia API.  It could be used to pull back camel routes, could be used to invoke other commands.  I hope the code is understandable enough to follow...! X-D

## Configuration

The application/script requires the following variables to be set...

1.  System.setProperty("kubernetes.master", "https://10.1.2.2:8443")
2.  System.setProperty("kubernetes.namespace", "amq")
3.  System.setProperty("kubernetes.labelname", "application")
4.  System.setProperty("kubernetes.labelvalue", "broker")
5.  System.setProperty("kubernetes.auth.token", "oTipQVOtgGcpJLGPh3-rLgHcSGPm77S79CTKuZ5yZpA")  // this is your "oc whoami -t"

## Supported "Commands"

- queuesize
- queuepurge <queue.name>

## Output Like so...

```
2016-09-16 15:06:50 INFO  DefaultCamelContext:3570 - Route: route2 started and consuming from: Endpoint[direct://queuesize]
2016-09-16 15:06:50 INFO  DefaultCamelContext:3570 - Route: route3 started and consuming from: Endpoint[direct://queuepurge]
2016-09-16 15:06:50 INFO  DefaultCamelContext:3570 - Route: route4 started and consuming from: Endpoint[direct://aggregate]
2016-09-16 15:06:50 INFO  DefaultCamelContext:3570 - Route: route5 started and consuming from: Endpoint[direct://generateTable]
2016-09-16 15:06:50 INFO  DefaultCamelContext:2840 - Total 5 routes, of which 5 are started.
2016-09-16 15:06:50 INFO  DefaultCamelContext:2841 - Apache Camel 2.17.3 (CamelContext: camel-1) started in 0.669 seconds
Do tell me how I can serve the greater good...: queuesize
┌────────────────────┬──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┬───────────┐
│ broker             │ mbean                                                                                                            │ QueueSize │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-ac7bm │ org.apache.activemq:brokerName=broker-amq-4-ac7bm,destinationName=queue.hello1,destinationType=Queue,type=Broker │ 7         │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-ac7bm │ org.apache.activemq:brokerName=broker-amq-4-ac7bm,destinationName=queue.hello2,destinationType=Queue,type=Broker │ 0         │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-ac7bm │ org.apache.activemq:brokerName=broker-amq-4-ac7bm,destinationName=queue.hello3,destinationType=Queue,type=Broker │ 0         │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-ac7bm │ org.apache.activemq:brokerName=broker-amq-4-ac7bm,destinationName=queue.hello4,destinationType=Queue,type=Broker │ 4         │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-ac7bm │ org.apache.activemq:brokerName=broker-amq-4-ac7bm,destinationName=queue.hello5,destinationType=Queue,type=Broker │ 0         │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-vspe5 │ org.apache.activemq:brokerName=broker-amq-4-vspe5,destinationName=queue.hello1,destinationType=Queue,type=Broker │ 0         │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-vspe5 │ org.apache.activemq:brokerName=broker-amq-4-vspe5,destinationName=queue.hello2,destinationType=Queue,type=Broker │ 0         │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-vspe5 │ org.apache.activemq:brokerName=broker-amq-4-vspe5,destinationName=queue.hello3,destinationType=Queue,type=Broker │ 0         │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-vspe5 │ org.apache.activemq:brokerName=broker-amq-4-vspe5,destinationName=queue.hello4,destinationType=Queue,type=Broker │ 0         │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-vspe5 │ org.apache.activemq:brokerName=broker-amq-4-vspe5,destinationName=queue.hello5,destinationType=Queue,type=Broker │ 0         │
└────────────────────┴──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┴───────────┘

Do tell me how I can serve the greater good...: queuepurge queue.hello1
OK!
Do tell me how I can serve the greater good...: queuesize
┌────────────────────┬──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┬───────────┐
│ broker             │ mbean                                                                                                            │ QueueSize │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-ac7bm │ org.apache.activemq:brokerName=broker-amq-4-ac7bm,destinationName=queue.hello1,destinationType=Queue,type=Broker │ 0         │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-ac7bm │ org.apache.activemq:brokerName=broker-amq-4-ac7bm,destinationName=queue.hello2,destinationType=Queue,type=Broker │ 0         │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-ac7bm │ org.apache.activemq:brokerName=broker-amq-4-ac7bm,destinationName=queue.hello3,destinationType=Queue,type=Broker │ 0         │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-ac7bm │ org.apache.activemq:brokerName=broker-amq-4-ac7bm,destinationName=queue.hello4,destinationType=Queue,type=Broker │ 4         │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-ac7bm │ org.apache.activemq:brokerName=broker-amq-4-ac7bm,destinationName=queue.hello5,destinationType=Queue,type=Broker │ 0         │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-vspe5 │ org.apache.activemq:brokerName=broker-amq-4-vspe5,destinationName=queue.hello1,destinationType=Queue,type=Broker │ 0         │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-vspe5 │ org.apache.activemq:brokerName=broker-amq-4-vspe5,destinationName=queue.hello2,destinationType=Queue,type=Broker │ 0         │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-vspe5 │ org.apache.activemq:brokerName=broker-amq-4-vspe5,destinationName=queue.hello3,destinationType=Queue,type=Broker │ 0         │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-vspe5 │ org.apache.activemq:brokerName=broker-amq-4-vspe5,destinationName=queue.hello4,destinationType=Queue,type=Broker │ 0         │
├────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────┤
│ broker-amq-4-vspe5 │ org.apache.activemq:brokerName=broker-amq-4-vspe5,destinationName=queue.hello5,destinationType=Queue,type=Broker │ 0         │
└────────────────────┴──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┴───────────┘

Do tell me how I can serve the greater good...: 
```