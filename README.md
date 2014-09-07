# Example code for Master Singleton HA

### Usage

sbt pack

target/pack/bin/main 2551
target/pack/bin/main 2552
target/pack/bin/main 2553

Shutdown one of the jvm, the master singleton will be transfered to another node.