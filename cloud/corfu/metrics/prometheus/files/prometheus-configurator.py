import argparse
import os
import yaml


def generate_fqdn_list(statefulset, headless, namespace, replica, port):
    return ["{}-{}.{}.{}.svc.cluster.local:{}".format(statefulset, str(i), headless, namespace, port) for i in
            range(replica)]


def generate_config(args):
    # load template
    with open(args.template, 'r') as template_file:
        prometheus_config_yaml = yaml.safe_load(template_file)

    with open(args.template, 'w') as yaml_config_file:
        # generate FQDN
        fqdn_list = generate_fqdn_list(
            args.statefulset,
            args.headless,
            os.environ["POD_NAMESPACE"],
            args.replica,
            args.port
        )
        fqdn_list.append('localhost:9000')

        # fill the template
        prometheus_config_yaml['scrape_configs'][0]['static_configs'][0]['targets'] = fqdn_list
        yaml.dump(prometheus_config_yaml, yaml_config_file)


def main():
    parser = argparse.ArgumentParser(description='Corfu prometheus initializer.')
    parser.add_argument('--port', '-p', type=str, required=True, help='The port Corfu is listening on.')
    parser.add_argument('--template', '-t', type=str, required=True, help='The path of the prometheus template config.')
    parser.add_argument('--replica', '-r', type=int, required=True, help='The replica of Corfu cluster.')
    parser.add_argument('--statefulset', type=str, default='corfu', help='Corfu statefulset name.')
    parser.add_argument('--headless', type=str, default='corfu-headless', required=True,
                        help='Corfu headless service name.')
    args = parser.parse_args()

    generate_config(args)


main()
