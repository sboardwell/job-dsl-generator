String orgRepo = 'sboardwell/job-dsl-generator'
String branchStr = 'master'
String jobName = 'job-dsl-generator-example-projectC'
String scriptPathStr = 'examples/projectB/Jenkinsfile'

Class JobDslGenUtil = new GroovyClassLoader(Thread.currentThread().getContextClassLoader()).parseClass(new URL("${UTIL_SCRIPT_URL}${binding.hasVariable('UTIL_SCRIPT_TOKEN') ? "?token=${binding.getVariable('UTIL_SCRIPT_TOKEN')}" : ''}").text);

// simple pipeline
def job1 = pipelineJob("${jobName}-eg-1") {
    definition {
        cpsScm {
            scm {
                git {
                    branch(branchStr)
                    remote {
                        github("${orgRepo}", 'https')
                    }
                }
            }
            lightweight()
            scriptPath(scriptPathStr)
        }
    }
}
JobDslGenUtil.addExtrasUsingDslJobDefinition(job1, JENKINS_URL, JENKINS_CREDS, out)

// with display name and description
def job2 = pipelineJob("${jobName}-eg-2") {
    definition {
        displayName "${jobName}-eg-2 Fancy Display"
        description "And a description..."
        cpsScm {
            scm {
                git {
                    branch(branchStr)
                    remote {
                        github("${orgRepo}", 'https')
                    }
                }
            }
            lightweight()
            scriptPath(scriptPathStr)
        }
    }
}
JobDslGenUtil.addExtrasUsingDslJobDefinition(job2, JENKINS_URL, JENKINS_CREDS, out)