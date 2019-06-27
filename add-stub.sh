#!/bin/sh

rmic -d stub -classpath target/classes -keep com.cairn.rmi.server.TaskManagerImpl
mv stub/com/cairn/rmi/server/TaskManagerImpl_Stub.java src/main/com/cairn/rmi/server
rm -r stub
