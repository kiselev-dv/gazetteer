#!/bin/bash

source tg.cred

function send_status {
  if [ ! -f /home/dkiselev/last-msg.txt ]; then  
    curl -G --data-urlencode "chat_id=$CHAT" --data-urlencode "text=${1}" $TELEGRAM_URL
    echo "date" > /home/dkiselev/last-msg.txt
    echo "$1" >> /home/dkiselev/last-msg.txt
  fi
}

lines=$(ps ax | grep java | grep -i gazetteerweb.jar | grep -v sudo | wc -l)

if [ "$lines" == 0 ]; then
    msg="There is no GazetteerWeb process running"
    send_status "$msg"
    exit 1
fi

features=$(curl --silent "http://osm.me/api/health.json" | python -m json.tool | grep "features" | grep -o '[0-9]*')

if [[ ${features:-0} == 0 ]]; then
    msg="Cant get features counter from health api"
    send_status "$msg"
    exit 1
fi

disk=$(df -h | grep -w '/' | tr -s ' '| cut -d' ' -f 5 | grep -o '[0-9]*')

if [[ ${disk} > 95 ]]; then
    msg="Disk usage is more than 95%"
    send_status "$msg"
fi


exit 0
