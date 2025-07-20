# Load Balancer
This is a simple load balancer implementation building on the Java standard library. The load balancer currently only supports round robin balancing. The load balancer will query the given servers every 5 seconds to check their health, if a server is unresponsive it will not be considered in the round robin scheduling. 
