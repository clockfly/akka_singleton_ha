# Quorum Aware Master Singleton HA with Distributed In-Memory Consistent State

### Usage

<pre>

sbt pack

## start 3 master nodes
target/pack/bin/main 2551
target/pack/bin/main 2552
target/pack/bin/main 2553

# start a client to register a application to master, check the log on node2551
target/pack/bin/client 127.0.0.1 2551

# kill master node 2551, master singleton and state will be started on another node master node node2552.
[Ctrl C] to kill [pid of process 2551]

# Start another client to modify state on node2552
target/pack/bin/client 127.0.0.1 2552

# Start 2551 again
target/pack/bin/main 2551

# kill master node2552, the master singleton and state will be migrated to master node3
[Ctrl C] to kill [pid of process 2551]

# Now, let's check how quorum take effect
# kill master node2551 and master node2552, since we no longer have a quorum, all process(master node2551, node2552, and node2553) will exit.
[Ctrl C] to kill [pid of process 2551]
[Ctrl C] to kill [pid of process 2552]

# We expect node2553 will also exit now, quorum take effect!!!

</pre>