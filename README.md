# NPMS-Registry-Data-Analysis

For this project I used a case class named NpmsObject and on that case class I
used 5 functions for different functionalities which I will describe as below:

Case Class:

NpmsObject:
This is the case class with three parameters which are name, githubvalues and
evaluationvalues. Here we plan to store all the data we extract from the NPMS registry.

Functions:

requestApi:
This function is inside the case class. Through this we will be able to get the
data from the NPMS registry and finally return the values through the object NpmsObject.

filterStars:
This function will filter out all the packages with less than 20 stars. To those
objects we have made the github values and evaluation values null.

discardTests:
This function will filter out all the packages with tests less than 50%. To those
Objects we have made the github values and evaluation values null.

filterFrequency:
This function will filter out all the packages with a release frequency less than
20%. To those objects we made the github values and evaluation values null.

filterDownload:
This function will filter out all the packages which has less than 100 downloads.
To those objects we made the github values and evaluation values null.

Usage of GraphDSL:

We have made use of GraphDSL in two places in this project. We used Flow shapes
which are pipelineFilters and flowThreePipelines.

pipelineFilters:
Through this pipeline all the packages were discarded which was specified.




flowThreePipelines:
Through this pipeline, the pipelineFilters passes 3 times. We used balance with
3 output ports where the pipelinefilters pass and it all come to merge which has 3 input ports.

Description of Full Flow:

<img width="805" alt="Screenshot 2023-11-02 at 10 15 00 PM" src="https://github.com/Mishkat96/NPMS-Registry-Data-Analysis/assets/47037691/adf17a18-c770-41dc-8012-51863e9a7aae">

According to our flow diagram, we first start with path from where we take the zip file
with all the packages names. We take the path as the source here. From the source we first
unzip the file using the function gunzip(). The output file is a ByteString file to which we
convert it to String. Afterwards, we have splitted to new lines.

In the next step, using the names we made objects by passing the names to the case
class NpmsObject. Then we passed these packages to the next step where we implemented
a backpressure strategy where we buffered the packages with maximum 20 packages at a
time. The next step is timeDelay where we made sure that 1 package streams every 3 seconds.
We implemented it with the function throttle().

From this step, we started calling the functions we made in our case class. First, we
called the function requestApi() where we pass the names of the packages and it returns the
object NpmsObject with name, github values and evaluation values in it.

The Flow shapes start from this stage. The output we got from the last stage then goes
to the flow shape flowThreePipelines with a balance of 3 output ports. From those ports the
flow shape “pipelineFilters” passes and goes to a merge which has 3 input ports. In the
“pipelineFilers” the packages goes through starShape, then testShape, then downloadShape
and then frequencyShape from where they filtered out all the unnecessary data. Finally, the
output goes to sinkToTerminal where it prints out. We can also change the sink to
“sinkToTextFile” where the output can be print.
