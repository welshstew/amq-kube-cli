log4j {
    appender.stdout = "org.apache.log4j.ConsoleAppender"
    appender."stdout.layout"="org.apache.log4j.PatternLayout"
    appender."stdout.layout.ConversionPattern"="%d [%-15.15t] %-5p %-30.30c{1} - %m%n"
    rootLogger="DEBUG,stdout"
}