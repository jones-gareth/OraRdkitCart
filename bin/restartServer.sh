dir=$(dirname $(readlink -f $0))
cd $dir
./stopServer.sh
sleep 5
./runServer.sh
