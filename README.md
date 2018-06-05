# waslp-fileSync

This project creates a Liberty feature that monitors files and pushes them to the specified registered hosts.
This is accomplished by polling the registered files for creates and updates, then using the file transfer mbean to push these changes to the registered hosts.
This feature can be installed on either the collective host or collective member.  Whichever has the files youw ant to monitor.

For instance, you could define a included xml file on the server, update it, and the changes will automatically be made available on all the collective members.
This could also be used for deploying new web apps to collective members.

Creates, deletes, and updates are all tracked and synced.

Since this feature uses the FileTransfer MBean to upload one or more files, either the remoteFileAccess element must be specified in the server.xml of the server that will receive the file(s), or the target directory must exist on the hostWriteList for the registered host.

Additionally this feature must be installed on a server with both the `json-1.0` and `jaxrsClient-2.0` features enabled.

## Building the Liberty feature
Currently this repository exists as an eclipse project.  Eventually it will be converted to a maven or gradle project.

## Using the Liberty feature

The Liberty feature can be added to a Liberty profile installation using the `featureManager` command as follows:

```bash
wlp\bin\featureManager install fileSync-1.0.0.esa
```

A server instance wishing to use the feature should add the `usr:fileSync-1.0` feature to the `featureManager` stanza in `server.xml`.
The server must (at least) be using Java 7.


### Configuration

The feature also adds the ability to specify sync configuration as part of the Liberty `server.xml`.
This is achieved by adding a `fileSync` stanza to the server.xml.
All the available attributes are listed below.
Note: You may use target system variables in `fileSync.policy.target.outputDir`, but you must use `%{variable_name}` to prevent normal expansion.


```xml
<fileSync id="fileSync" user="admin" password="{xor}PjsyNjE=" baseURL="https://localhost:9443" fileRegistry="${server.config.dir}/registry.json">
 <policy id="policy-1" pollInterval="5000" minSyncInterval="15000" recursive="true">
  <source>${server.config.dir}/include1.xml</source>
  <source>${server.config.dir}/include2.xml</source>
  <target hostName="localhost"
     serverName="server1"
     serverUserDir="/C:/users/Cody Moore/Liberty/usr"
     outputDir="%{server.output.dir}"/>
  <target hostName="host2,host3" outputDir="/C:/users/Cody Moore/Liberty/usr"/>
 <policy>
</fileSync>
```

Name              | Description
------------------|------------
id                | Unique configuration id.  Not required.
user              | User name to authenticate to mbean as
password          | Password for user
baseURL           | Base url of WebSphere Liberty http endpoint
fileRegistry      | If specified file updates will be written to this file.  This allows tracking file changes across server restarts.  Defaults to none.
policy            | A file sync policy of source files/directories to targets.  Multiple policies may be specified.
includeInRegistry | Include this policy in the file registry, if a file registry is used.  Defaults to true.
pollInterval      | How often (in milliseconds) to check for file changes.  Defaults to 5000.
minSyncInterval   | The minimum number of milliseconds that must pass between synchronizations.  This is to prevent too many subsequent calls. Defaults to 15000.
recursive         | Specify whether to recurse through source directories.  If set to false only the files right below the source directory will be monitored. Defaults to true.
source            | A source file or directory to monitor for changes.  If the file does not exist at startup, the policy wait for its creation, then trigger a CREATE event.
target            | A destination configuration to sync the file to
hostName          | Target hostName. A pattern,ex `*` may be used, or mutiple hostNames may be specified if comma delmited.  If anything but a single name is specified, serverName and serverUserDir are ignored.
serverName        | Target server name.  Must be specified in order to use target variables.
serverUserDir     | User directory for target server.  Must be specified in order to use target variables.
outputDir         | Output directory to send file to.  File will be named relative to source path.  For instance if outputDir is set to /opt/IBM and the monitored file is /tmp/dir1 and /tmp/dir1/config/include.xml is created, the destination file will will be /opt/IBM/config/include.xml

Updates to the `server.xml` are made available dynamically.
