# Contributing
## Installing Java

https://www.java.com/en/download/

## Installing Gradle

<pre>
$ curl -s get.sdkman.io | bash
$ sdk install groovy
$ sdk install gradle
</pre>

## Playing nice with Idea
This step only applies if you are using [Intellij's Idea](http://www.jetbrains.com/idea/) as your ide
<pre>
gradle idea
</pre>

## Running the server
The file `config.groovy` has settings that are applicable for running on the production server.  You may create a file
`config.local.groovy`, which if present will be merged into the config overriding any values.  Feel free to alter paths,
ports, timeouts and other settings by changing this file.  It is in the `.gitignore`.

### From gradle:
<pre>
gradle run
</pre>

And you should see some output similar to 
<pre>
oejs.ServerConnector:main: StartedServerConnector@73483dda{HTTP/.1,[http/1.1]}{0.0.0.0:8080}
oejs.Server:main: Started @3287ms
</pre>
You may have problems with `Ctrl+C` on Cygwin/windows with gradle being cancelled, but the server still running, and thus
not releasing the bind on the port.  In this case, you'll need to find and kill the java process keeping the port open 
every time you run a new server.

### From idea:
To run the server from Idea, setup a new Run/Debug configuration.  Add new configuration -> Application, set the Main 
class to `webtesting.MainClass`

# Endpoints:
## Tests
### tests/ (GET)
### tests/{suite} (GET)
### tests/{suite}/{id} (GET)
## Queue
### queue/ (GET)
### queue/{suite} (GET)
### queue/{suite}/{id} (GET)
### queue/{suite} (POST)
### queue/{suite}/{id} (PUT)
## Results
### results/ (GET)
### results/{suite} (GET)
### results/{suite}/{id} (GET)
## Diagnostics
### ping/ (GET)
### healthcheck/ (GET)
### healthcheck/lb (GET)
### healthcheck/nagios (GET)
