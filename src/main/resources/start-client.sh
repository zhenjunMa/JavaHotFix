#!/bin/bash

jvm_args="-XX:+UseG1GC  -Xmx256m -Xms256m -XX:-OmitStackTraceInFastThrow"

exec java -cp .:conf/*:lib/* ${jvm_args} com.lfls.hotfix.ClientBootStrap