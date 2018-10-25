#!/bin/bash

rm ./dist/*.jar

mvn clean package -DskipTests
cp ./target/media-ext-bot-*-jar-with-dependencies.jar ./dist/media-ext-bot.jar
