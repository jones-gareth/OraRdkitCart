#!/bin/sh
nohup java -Xmx32105M -Xss8M -cp '/home/gareth/src/OraRdkitCart/target/OraRdkitCart-1.0-SNAPSHOT-all.jar:'  com.cairn.rmi.server.TaskManagerImpl /home/gareth/src/OraRdkitCart/bin/server.properties &
