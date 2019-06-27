#!/usr/bin/env bash

rm -r ~/.m2/repository/org/rdkit
cp $RDBASE/build/Code/JavaWrappers/gmwrapper/libGraphMolWrap.so lib
cp $RDBASE/Code/JavaWrappers/gmwrapper/org.RDKit.jar repo/org/rdkit/rdkit/1.0-SNAPSHOT/rdkit-1.0-SNAPSHOT.jar
export LD_LIBRARY_PATH=/home/gareth/src/rdkit_test/lib:$LD_LIBRARY_PATH
mvn -DskipTests -U install

