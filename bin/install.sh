#!/bin/sh

java -Xss8M  --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED \
     -cp "./target/OraRdkitCart-1.0-SNAPSHOT-all.jar:" com.cairn.rmi.installer.InstallerSupervisor
