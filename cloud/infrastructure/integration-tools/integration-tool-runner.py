import os
import sys

aggregation_unit = sys.argv[1]

curr_dir = os.getcwd()

os.system("docker volume create --name " + aggregation_unit)

docker_path = os.popen("which docker").read().strip()

integration_tools_cmd = "docker run -ti --rm " \
                        "-v {aggregation_unit}:/data " \
                        "-v {curr_dir}/config.json:/app/config.json " \
                        "-v /var/run/docker.sock:/var/run/docker.sock " \
                        "-v {docker_path}:/bin/docker " \
                        "corfudb/integration-tools:latest " \
                        "bin/integration-tools.sh processing --config=config.json {aggregation_unit}" \
    .format(aggregation_unit=aggregation_unit, curr_dir=curr_dir, docker_path=docker_path)
os.system(integration_tools_cmd)

os.system("docker volume rm " + aggregation_unit)
