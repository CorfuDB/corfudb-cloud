require 'date'

def find_begin_index(message_list)
  indices = {}
  (0..message_list.length()).each do |index|
    msg = message_list[index]
    if !msg.nil? && !msg.empty?
      msg = msg.strip
      if msg.include?(":") && (msg.include?("AM") || msg.include?("PM"))
        indices['date'] = index
      elsif msg.start_with?("avg-cpu")
        indices['avg-cpu'] = index
      elsif msg.start_with?("Device")
        indices['Device'] = index
      elsif msg.start_with?("Total")
        indices['Device_End'] = index - 1
      elsif msg.start_with?("TID")
        indices['TID'] = index
      else
        next
      end
    end
  end
  return indices
end

def parse_date(start_index, end_index, message_list, event)
  (start_index..end_index).each do |index|
    msg = message_list[index]
    if !msg.nil? && !msg.empty? && (msg.end_with?("AM") || msg.end_with?("PM"))
      timestamp = msg.strip
      pattern = "%m/%d/%Y %I:%M:%S %p"
      parsed_timestamp = DateTime.strptime(timestamp, pattern).rfc3339
      parsed_timestamp["+00:00"] = ".000Z"
      event.set("new_timestamp", parsed_timestamp)
    end
  end
end

def parse_avg_cpu(start_index, end_index, message_list, event)
  avg_cpu_columns = []
  (start_index..end_index).each do |index|
    msg = message_list[index]
    if !msg.nil? && !msg.empty?
      if msg.start_with?("avg-cpu")
        avg_cpu_columns = msg.split(" ").map {|val| val.strip}[1..msg.length()]
      else
        avg_cpu = {}
        avg_cpu_rows = msg.split(" ").map {|val| val.strip.to_f}
        avg_cpu_columns.zip(avg_cpu_rows).each do |col, row|
           avg_cpu[col] = row
        end
         event.set("avg_cpu", avg_cpu)
       end
     end
   end
 end

 def parse_device_table(start_index, end_index, message_list, event)
   device_stats_columns = []
   (start_index..end_index).each do |index|
     msg = message_list[index]
     if !msg.nil? && !msg.empty?
       if msg.start_with?("Device")
         device_stats_columns = msg.split(" ").map {|val| val.strip}
       else
         device_stats_row = msg.split(" ").map {|val| val.strip}
         device_stats = {}
         device_stats_columns[1..device_stats_columns.length()].zip(device_stats_row[1..device_stats_row.length()]).each do |col, row|
            device_stats[col] = row.to_f
         end
         event.set(device_stats_row[0], device_stats)
       end
     end
   end
 end


 def parse_disk_stats(begin_index, msg_list, event)
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
  indices = find_begin_index(msg_list)
  date_start_index = indices['date']
  date_end_index = indices['avg-cpu']
  parse_date(date_start_index, date_end_index, msg_list, event)
  avg_cpu_start_index = indices['avg-cpu']
  avg_cpu_end_index = indices['Device'] - 1
  parse_avg_cpu(avg_cpu_start_index, avg_cpu_end_index, msg_list, event)
  device_table_start_index = indices['Device']
  device_table_end_index = indices['Device_End']
  parse_device_table(device_table_start_index, device_table_end_index, msg_list, event)
  tid_table_start_index = indices['TID']
  parse_disk_stats(tid_table_start_index, msg_list, event)
  return [event]
end
