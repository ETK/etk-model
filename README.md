# Enginerring Toolkit Kernel Java Library


## How to develop

* Project dependencies:
    + junit-3.8.5 (test)

* mvn dependency:tree
  <pre>

   
  </pre>


### Prepare your environment

Use [Apache Maven][maven] version 2.2.1 minimum. Version 3.x is recommended.


Or add the eXo platform repository in your maven settings (`${HOME}/.m2/settings.xml`) like this :

    <settings>
      ....
      <profiles>
        <profile>
          <id>exo-public</id>
          <repositories>
            <repository>
              <id>exo-public</id>
              <url>http://repository.exoplatform.org/public</url>
            </repository>
          </repositories>
        </profile>
        ....
      </profiles>
      ....
      <activeProfiles>
        <activeProfile>exo-public</activeProfile>
        ....
      </activeProfiles>
      ....
    </settings>

[maven]: http://maven.apache.org "Apache Maven"
[central]: http://repo1.maven.org "Maven Central Repository"

### Default build

Use this command to build project:

    mvn clean install

By default, it will run only unit tests.

To run integration tests you have two choices.


    
