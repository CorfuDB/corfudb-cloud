import argparse
import json
import sys
import os
import uuid

def generate_fqdn_list(statefulset, headless, namespace, replica, port):
  return ["{}-{}.{}.{}.svc.cluster.local:{}".format(statefulset, str(i), headless, namespace, port) for i in range(replica)]

def generate_layout(args):
  # load template
  layout_template_file = open(args.template)
  layout_template = json.load(layout_template_file)
  layout_template_file.close()

  # generate FQDN
  fqdn_list = generate_fqdn_list(args.statefulset, args.headless, os.environ["POD_NAMESPACE"], args.replica, args.port)

  # fill the template
  layout_template["layoutServers"] = fqdn_list
  layout_template["sequencers"] = fqdn_list
  layout_template["segments"][0]["stripes"][0]["logServers"] = fqdn_list
  layout_template["clusterId"] = "456e4567-e89b-12d3-a456-556642440001" if args.type == "source" else "456e4567-e89b-12d3-a456-556642440002"
  # print layout
  print("Generated layout:")
  print(json.dumps(layout_template, indent=2))

  # save the template
  layout_file = open(args.layout, "w")
  json.dump(layout_template, layout_file, indent=2)
  layout_file.close()

def main():
  parser = argparse.ArgumentParser(description='Corfu layout initilizer.')
  parser.add_argument('--port', '-p', type=str, required=True, help='The port Corfu is listening on.')
  parser.add_argument('--template', '-t', type=str, required=True, help='The path of the layout template json.')
  parser.add_argument('--layout', '-l', type=str, required=True, help='The path of the layout json.')
  parser.add_argument('--replica', '-r', type=int, required=True, help='The replica of Corfu cluster.')
  parser.add_argument('--statefulset', type=str, default='corfu', help='Corfu statefulset name.')
  parser.add_argument('--headless', type=str, default='corfu-headless', required=True, help='Corfu headless service name.')
  parser.add_argument('--type', type=str, default='source', required=True, help='Source or sink.')
  args = parser.parse_args()

  generate_layout(args)

try:
  main()
except Exception as e:
  print("failed to generate corfu layout:", e)
  sys.exit(1)