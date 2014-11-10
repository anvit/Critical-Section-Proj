My program accepts 2 command line parameters - the config file name and the node number for the process its being run. So for running it on 3 nodes one would execute - 

java ProjectSCTP config.txt 1
java ProjectSCTP config.txt 2
java ProjectSCTP config.txt 3

on 3 different nodes.

The format for my config file is as follows: The first line would have the number of nodes; subsecquent lines would have the host, the port and a space sepereated quorum list. For e.g.

4
dc01.utdallas.edu	3456	1 2 3
dc02.utdallas.edu	3131	1 2 4
dc03.utdallas.edu	6432	1 3 4
dc04.utdallas.edu	3454	2 3 4


I've included a sample configuration file with along with my code in my submission. On execution, Each process logs its entry and exit into the critical section in a file called CSlog.txt. In case of critical section overlaps, one would be able to see those in the file. Moreover the testing module detects any critical section overlaps and records the message "Multiple CS entry!! :" followed by the process number in the file.

The processes would have to be terminated manually once the code has finished running.