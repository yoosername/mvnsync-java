mvnsync-java
============

maven artifact sync test written in java

Examples
============
Display help:
java -jar mvnsync.jar -help

List differences between maven central and local repo:
java -jar mvnsync.jar -l "C:\test-repo" -r "http://repo1.maven.org/maven2" --list

Same as above but only concerned with zip artifacts:
java -jar mvnsync.jar -l "C:\test-repo" -r "http://repo1.maven.org/maven2" --list -t "zip"

Syncronize maven central to local repo using specified mirrors:
java -jar mvnsync.jar -l "C:\test-repo" -r "http://repo1.maven.org/maven2" -m "http://uk.maven.org/maven2,http://mirrors.ibiblio.org/pub/mirrors/maven2"

syncronize all artifacts specified in GAV file list from maven central:
java -jar mvnsync.jar -l "C:\test-repo" -r "http://repo1.maven.org/maven2" -m "http://uk.maven.org/maven2,http://mirrors.ibiblio.org/pub/mirrors/maven2" -f "C:/eclipse/deps.txt"

synchronize zip,tar and gz artifacts from maven central to local repo:
java -jar mvnsync.jar -l "C:\test-repo" -r "http://repo1.maven.org/maven2" -m "http://uk.maven.org/maven2,http://mirrors.ibiblio.org/pub/mirrors/maven2" -t "zip,tar.gz"

synchronize artifacts with groupId = "com.atlassian.plugins" from atlassian public repo to local repo
java -jar mvnsync.jar -l "C:\atlassian-test-repo" -r "https://maven.atlassian.com/content/repositories/atlassian-public" --groupId "com.atlassian.plugins"

validate local repository artifacts and fetch missing poms etc from central
java -jar mvnsync.jar -l "C:\test-repo" -r "http://repo1.maven.org/maven2" -m "http://uk.maven.org/maven2,http://mirrors.ibiblio.org/pub/mirrors/maven2" -v