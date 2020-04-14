require 'date'

def parse_disk_stats(msg_list, begin_index, event)
  columns = []
  (begin_index..msg_list.length()).each do |index|
    msg = msg_list[index]
    if !msg.nil? && !msg.empty?
      msg = msg.strip
      if msg.start_with?("Total") || msg.start_with?("Actual")
        next
      elsif msg.start_with?("TID")
        columns = msg.split(' ')
      else
        proc_stats = {}
        data = msg.split(' ')
        data.delete('%')
        stats = data[0..columns.length() - 2]
        command = data[columns.length() - 1..data.length()].join(' ')
        stats.push(command)
        columns.zip(stats).each do |column, value|
          value = value.strip
          # strings
          if ["TID", "PRIO", "USER", "READ", "WRITE", "COMMAND"].include? column
            proc_stats[column] = value
          # floats
          else
            proc_stats[column] = value.to_f
          end
        end
        user = stats[2]
        if event.get(user).nil?
          event.set(user, [])
        end
        process_list_under_user = event.get(user)
        process_list_under_user.push(proc_stats)
        event.set(user, process_list_under_user)
      end
    end
  end
end

def filter(event)
  message = event.get("message")
  msg_list = message.split("\n")

  avg_cpu = {}
  is_avg_cpu = false
  avg_cpu_columns = []

  is_in_table = false
  device_stats_columns = []
  for curr_line in msg_list
     # Parse timestamp
     if curr_line.end_with?("AM") || curr_line.end_with?("PM")
        timestamp = curr_line.strip
        pattern = "%m/%d/%Y %I:%M:%S %p"
        parsed_timestamp = DateTime.strptime(timestamp, pattern).rfc3339
        parsed_timestamp["+00:00"] = ".000Z"
        event.set("new_timestamp", parsed_timestamp)
     end
     # Parse average cpu:
     if curr_line.start_with?("avg-cpu")
       is_avg_cpu = true
       avg_cpu_columns = curr_line.split(" ").map {|val| val.strip}[1..curr_line.length()]
       next
     end
     if is_avg_cpu && !avg_cpu_columns.empty?
       avg_cpu_rows = curr_line.split(" ").map {|val| val.strip.to_f}
       avg_cpu_columns.zip(avg_cpu_rows).each do |col, row|
         avg_cpu[col] = row
       end
       is_avg_cpu = false
       avg_cpu_columns = []
       event.set("avg_cpu", avg_cpu)
       avg_cpu = {}
     end
     # Parse device stats:
     if curr_line.start_with?("Device")
       is_in_table = true
       device_stats_columns = curr_line.split(" ").map {|val| val.strip}
       next
     end
     if is_in_table && !device_stats_columns.empty?
        if !curr_line.nil? && !curr_line.empty?
          device_stats_row = curr_line.split(" ").map {|val| val.strip}
          device_stats = {}
          device_stats_columns[1..device_stats_columns.length()].zip(device_stats_row[1..device_stats_row.length()]).each do |col, row|
            device_stats[col] = row.to_f
          end
          event.set(device_stats_row[0], device_stats)
        else
          is_in_table = false
          device_stats_columns = []
        end
    end
  end
  parse_disk_stats(msg_list, 20, event)
  return [event]
end
