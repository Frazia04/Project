How to setup:
For the checker to work in windows LLVM https://releases.llvm.org/download.html#10.0.0 needs to be installed
When running the exclaim for the first time with the similaritychecker feature, you need to build the c program following the instructions
in this repository: https://softech-git.informatik.uni-kl.de/exclaim-project-ss20/plagiatschecker-backend-team1 and then either place the
"SimilarityChecker.exe" for windows or the "SimilarityChecker"-file for Linux in the folder containing this file.
Then for Linux make it runnable by using chmod +x SimilarityChecker in your terminal

Folder structure:
This folder contains all the necessary files for the similarity checker functionality.
Most of the files here are created automatically, so it's best not to change anything, if you don't know what you are doing.
/similarityChecker/files/exerciseID/sheetID/
checks contains all the data that the checker outputted in txt files for each assignment (altered for better usability)
paths contains all the paths to the files for each assignment
logs contains error output for checks

To update the Checker:
Follow the instructions in the SimilarityChecker dependency mentioned above to build a new SimilarityChecker.exe
and (or) SimilarityChecker executable file for Linux.
Now make the Linux file executable with chmod +x SimilarityChecker,
then simply replace the old SimilarityChecker.exe and the SimilarityChecker file for Linux in this folder with the new one.

Basic functionality of the "frontend":
An assistant needs to start the check individually for each sheet when needed, by navigating to "Ähnlichkeitsprüfer" in the overview page of an exercise.
This will create a AissgnmentNo_Paths.txt in the correct paths directory as mentioned above.
For each of these files the java code in TestController.java will run a check, by starting the SimilarityChecker with all the given paths as arguments.
It then reads the output, and prints it to the correct checks file in a format that's better to read.
Any occurring errors during checking outputted by the SimilarityChecker will be printed into the correct file in the logs directory
When a tutor/assistant accesses the "Ähnlichkeitsprüfer"-Page mentioned above, they will now get a display of the results.

Where to find what:
The button to reach the results page for the checker can be found in the exercise.html, simply remove it to disable similarityChecker functionality.
Most of the functionalities are in the SimilarityCheckerDao class.
The request mapping for starting a check is in the TestController class.
The request mapping for viewing the results is in the ExerciseController class.
The html can be found in similarityScore.html and similarityScoresForAssignments.html.
The javascript for the "start tests"-button functionality is in similarityCheckerScript.js
A class to make passing around the data of one score easier was created as SimilarityScore.



