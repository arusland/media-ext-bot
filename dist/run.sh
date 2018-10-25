#!/bin/bash

sdir="$(dirname $0)"

echo "Script dir=$sdir"

cd $sdir

echo "Killing all started media-ext-bot instances..."
pgrep -a -f media-ext-bot.jar | awk '{print $1;}' | while read -r a; do kill -9 $a; done

java -jar media-ext-bot.jar &
