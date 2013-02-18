HACK
====

Super fast hack to grab all the information about your car(s) every 5 minutes and log them to a file. Pass
in a filename with contents that are formatted like:

    email=....
    password=....

You need to use your mytesla login credentials. To build the application use maven:

    mvn install

Or download it from my Dropbox: http://dl.dropbox.com/u/3924269/teslalogger.jar

Execute the application like:

    java -jar teslalogger.jar -c auth.properties

If you nohup it you can run it in the background on a server somewhere.

Based on: [http://docs.timdorr.apiary.io]