rootProject.name = "corfu-cloud-native-ecosystem"

include("corfu:corfu-client-example")

include("infrastructure:kibana:kibana-tools")
include("infrastructure:logstash:logstash-tools")
include("infrastructure:integration-tools")
include("infrastructure:filebeat:filebeat")
include("infrastructure:logstash:logstash")

