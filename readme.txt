My program accepts 2 command line parameters - the config file name and the node number for the process its being run. So for running it on 3 nodes one would execute - 

java ProjectSCTP config.txt 1
java ProjectSCTP config.txt 2
java ProjectSCTP config.txt 3

on 3 different nodes.

The format for my config file is as follows: The first line would have the number of nodes; subsecquent lines would have the host, the port and the complete path its supposed to take seperated by commas. The path list is seperated by space. For e.g.

5
dc01.utdallas.edu	3456	2 3 4 5 1 
dc02.utdallas.edu	3131	1 3 5 4 2
dc03.utdallas.edu	6432	1 4 5 2 3
dc04.utdallas.edu	3454	3 1 2 5 4
dc05.utdallas.edu	5745	4 2 1 3 5

I've included a sample configuration file with along with my code in my submission. The final result would be displayed on each process as soon as its paths has been traversed. 

This is how a sample run looks like on one of the nodes:

Self label:96
Server started at - 3131
Label value received:154
.-----------------------.
|Final Value : 250		|
'-----------------------'
Label value received:0
Sending - localhost:6432> 4 5 1-96
Port in use, retrying...
Label value received:5
Sending - localhost:3456> 3 5-101
Label value received:93
Sending - localhost:6432> *-189
Label value received:120
Sending - localhost:5745> 4-216

Here Self Label is the label generate by the node. Sending - localhost:6432> 4 5 1-96 denotes that the message "4 5 1-96" is being sent to localhost:6432. If a port(Not the one specifiec in config files; the port used by SCTP in client code) is being used by another process, it will display "Port in use, retrying..." and it will look for another port to start the client on. The final value (as shown above) might not be displayed at the very end; its displayed as soon as the process's path is complete and some in transit messages for other process's paths might still be printed. 

The processes would have to be terminated manually once the code has finished running.