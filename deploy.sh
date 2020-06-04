#!/bin/bash

source secvars.sh

echo "Deploying to $DEPLOY_HOST..."

scp -r ./dist root@$DEPLOY_HOST:/home/media-ext-bot

ssh root@$DEPLOY_HOST 'sh /home/media-ext-bot/dist/run.sh'
