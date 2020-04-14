# job-dsl-generator

A util script to help bridge the gap between job configuration through the [Jenkins Job DSL Plugin](https://github.com/jenkinsci/job-dsl-plugin) vs through the [declarative Jenkins Pipeline Syntax](https://jenkins.io/doc/book/pipeline/syntax/#options).

## Usage and Examples

These scripts will be improved over time but for now you can try out the examples found in the examples directory.

* fork this repo
* create the seed pipeline job as per [examples/seed-project/Jenkinsfile]
* the util script needs a set of credentials (username + access-token for an authorised user, does not need to be an admin), so either:
  * add some `jenkins-creds` credentials
   or
  * change the `jenkins-creds` to the credentials of your choice

  To be continued...