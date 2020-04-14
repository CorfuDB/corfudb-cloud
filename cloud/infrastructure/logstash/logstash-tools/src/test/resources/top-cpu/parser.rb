require 'date'


def parse_ts(timestamp)
  timestamp = timestamp.strip.split(" ")
  day_of_week = timestamp[0]
  timestamp[0] = timestamp[0] + ","
  timestamp[1], timestamp[2] = timestamp[2], timestamp[1]
  timestamp[3], timestamp[-1] = timestamp[-1], timestamp[3]
  timestamp[-2], timestamp[-1] = timestamp[-1], timestamp[-2]
  timestamp[-1] = "+0000"
  parsed_timestamp = DateTime.rfc2822(timestamp.join(" ")).to_s
  parsed_timestamp["+00:00"] = ".000Z"
  return parsed_timestamp
end

def parse_load_avg(line)
  load_averages = {}
  averages = line.split(",")[-3..line.length()]
  averages[0].sub!("load average:", "")
  averages = averages.map {|val| val.strip.to_f}
  load_averages["1 min"] = averages[0]
  load_averages["5 min"] = averages[1]
  load_averages["15 min"] = averages[2]
  return load_averages
end

def parse_cpu_perc(line)
  cpu_usage = {}
  line = line.split(" ")
  titles = ["user cpu time", "system cpu time", "user nice cpu time", "idle cpu time", "io wait cpu time", "hardware irq", "software irq", "steal time"]
  titles.zip(line[1..line.length()]).each do |title, metric|
    cpu_usage[title] = metric.strip.to_f
  end
  return cpu_usage
end

def parse_memory(line)
  mem_kib = {}
  stats = line.split(":")
  stats = stats[1].split(",")
  titles = ["total", "free", "used", "buff/cache"]
  titles.zip(stats).each do |title, metric|
    mem_kib[title] = metric.split(" ")[0].to_i
  end
  return mem_kib
end

def parse_swap(line)
  swap_kib = {}
  stats = line.split('.')
  swap = stats[0].split(':')[1].split(',')
  avail = stats[1]
  titles = ["total", "free", "used"]
  titles.zip(swap).each do |title, metric|
    swap_kib[title] = metric.split(" ")[0].to_i
  end
  swap_kib['available_memory'] = avail.split(" ")[0].to_i
  return swap_kib
end

def filter(event)
    message = event.get("message")
    msg_list = message.split("\n")
    ts = parse_ts(msg_list[0])
    event.set("ts", ts)
    load_avg = parse_load_avg(msg_list[1])
    event.set("load_avg", load_avg)
    cpu_perc = parse_cpu_perc(msg_list[3])
    event.set("cpu_perc", cpu_perc)
    mem_kib = parse_memory(msg_list[4])
    event.set("mem_kib", mem_kib)
    swap_kib = parse_swap(msg_list[5])
    event.set("swap_kib", swap_kib)
    return [event]
end
