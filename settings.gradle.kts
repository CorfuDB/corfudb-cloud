rootProject.name = "corfu-cloud-native-ecosystem"

include("benchmarks", "tests")

include("cloud:infrastructure:kibana:kibana-tools")
include("cloud:infrastructure:logstash:logstash-tools")
include("cloud:infrastructure:integration-tools")
include("cloud:infrastructure:filebeat:filebeat")
include("cloud:infrastructure:logstash:logstash")

