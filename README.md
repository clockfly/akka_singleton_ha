# Example code for Master Singleton HA

### Usage

<pre>

sbt pack

target/pack/bin/main 2551
target/pack/bin/main 2552
target/pack/bin/main 2553
</pre>

Shutdown one of the jvm, the master singleton will be transfered to another node.
