counter=0
iname=$(ls /sys/class/net | grep -i ens)

echo " *** interface name is:: $iname *** "

while [ $counter -lt 2 ]
do
  echo "*** DOWN the interface $iname ***"
  ip link set $iname down > /dev/null
  isPing=$?

  if [ $isPing = 0 ]; then
    echo " *** Iface brought down successfully ***"
  else
    echo " *** faild to bring down the interface '$iname' ***"
    echo " exiting ... "
    exit 0
  fi

  echo "*** sleeping for 5sec, after bringing DOWN the interface '$iname' ***"
  sleep 5s

  echo "*** UP the interface $iname ***"
  ip link set $iname up > /dev/null
  isPing=$?

  if [ $isPing = 0 ]; then
    echo " *** Iface brought up successfully ***"
  else
    echo " *** faild to bring up the interface '$iname' ***"
    echo " exiting ... "
    exit 1
  fi

  echo "*** sleeping for 5sec, after bringing UP the interface '$iname' ***"
  sleep 5s

  counter=`expr $counter + 1`
  echo " \n *** counter:: $counter is finished *** \n"
done

echo " *** script completed *** "
