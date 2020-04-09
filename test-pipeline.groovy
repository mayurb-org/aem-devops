pipeline {
 
  agent any
 
  options {
    buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '15', numToKeepStr: '15'))
   // ansiColor('xterm')
    disableConcurrentBuilds()
  }
 
/* environment {
    HYBRIS_ZIP_FILE = 'HYBRISCOMM180800P_3-70003534.ZIP'
    MYSQL_DRIVER_VER = '5.1.47'
  }*/
 
  tools {
   // jdk 'JAVA_8_71'
    nodejs 'NodeJS-11'
  }
 
 parameters {
      string(defaultValue: 'dev-testing-1', description: 'Hybris Code Branch/TAG Name for Checkout', name: 'BRANCH')
      string(defaultValue: 'throwaway', description: 'Hybris Code Branch/TAG Name for Checkout', name: 'Origin')
      booleanParam(defaultValue: true, description: 'UI Build Execution', name: 'UI_Build')
      booleanParam(defaultValue: true, description: 'Hybris Junit Tests Execution', name: 'JunitTest')
      booleanParam(defaultValue: true, description: 'Hybris Sonar Analysis Execution', name: 'Sonar')
      booleanParam(defaultValue: true, description: 'Hybris Production ZIP Creation', name: 'Artifact')
      string(defaultValue: 'CO-Sonar', description: 'Infra Branch/TAG Name for Checkout', name: 'INFRA_BRANCH')
      booleanParam(defaultValue: true, description: 'Select if you need deployment also', name: 'Deploy')
      choice choices: ['dev2', 'upgradeqa'], description: 'Environment name to be deploy ', name: 'environment'
     
                }
 
  stages {
   
      
                                stage ('Checkout Hybris Code') {
        steps {
          echo "====== Current WORKSPACE Directory is ${env.WORKSPACE} ======="
          echo "============== GIT Hybris Source Code : ${BRANCH} =============="
          checkout(
            [
              $class: 'GitSCM',
              branches: [[name: '${origin}/${BRANCH}']],
              doGenerateSubmoduleConfigurations: false,
              extensions: [
                /*[$class: 'CleanBeforeCheckout'],*/
               /* [$class: 'CloneOption', depth: 0, noTags: false, reference: '', shallow: false, timeout: 120],*/
                [$class: 'CheckoutOption', timeout: 120]
               /*[$class: 'CleanBeforeCheckout'],*/
                /*[$class: 'PruneStaleBranch']*/
                ],
                submoduleCfg: [],
                userRemoteConfigs: [
                  [
                    credentialsId: 'vaibhav',
                    url: 'http://tfs.clasohlson.se:8080/tfs/DefaultCollection/Hybris/_git/HybrisGitDevelopmentRepo'
                    ]
                  ]
                ],
              )
          }
        }
       
    stage ('Checkout Infra Repo') {
          steps {
            echo "====== Current WORKSPACE Directory is ${env.WORKSPACE} ======="
            echo "============== GIT Infra Code : ${INFRA_BRANCH} =============="
            checkout ( poll: false, scm: [
              $class: 'GitSCM',
              branches: [[name: '${INFRA_BRANCH}']],
              doGenerateSubmoduleConfigurations: false,
              extensions: [
                [$class: 'RelativeTargetDirectory', relativeTargetDir: 'Infrastructure']
                ],
                submoduleCfg: [],
                userRemoteConfigs: [
                  [
                  credentialsId: 'vaibhav',
                    url: 'http://tfs.clasohlson.se:8080/tfs/DefaultCollection/Hybris/_git/HybrisGitInfraRepo'
                    ]
                  ]
                ],
              )
            }
          }
    stage ('Chcek With Master Banch') {
        steps {
            sh '''
            #!/bin/bash
           
            REV_DEV=$(git rev-parse origin/master)
            REV_HEAD=$(git rev-parse HEAD)
            echo ;
            echo "Release is at : $REV_DEV"
            echo "Validating commits on branch \"${BRANCH_NAME}\" at revision : $REV_HEAD"
            echo ;
            GIT_RESULT=$(git log | grep $REV_DEV | awk '{print $2}')
            sleep 5
           
            if [ "$REV_DEV" != "$GIT_RESULT" ]; then
                            echo "[ERROR] The branch is not in sync with master branch."
                            echo "[ERROR] Branch validation job aborted . . . (Need pull from Master) ";
                            echo ;
                            echo ;
                           # exit 1
            else
                            echo "[INFO] SUCCESS !"
            fi
            '''
        }
    }
     stage ('CI Properties Setup') {
          steps {
            echo "====== Current WORKSPACE Directory is  ======="
            echo "====== Copying Local Properties & updating properties for CI Environments ======"
              sh '''
                #set -x
                cd Code/hybris/config/
                rm -f local.properties
                mv local_ci.properties local.properties
                cat sonar.properties >> local.properties
                sonar_projectVersion=`echo ${BUILD_NUMBER}`
                echo sonar.projectVersion=Build_${sonar_projectVersion} >> local.properties
                echo sonar.projectKey=CO_Upgrade >> local.properties
                echo sonar.projectName=CO_Upgrade >> local.properties
                echo -e "\nmini.release.version=${BUILD_NUMBER}" >> local.properties.base
                rm -rf ${WORKSPACE}/Code/hybris/scripts
                cp -pr ${WORKSPACE}/Infrastructure/scripts ${WORKSPACE}/Code/hybris/
                rm -f ${WORKSPACE}/Code/hybris/bin/platform/build.xml
                cp ${WORKSPACE}/Infrastructure/scripts/build_1811.xml ${WORKSPACE}/Code/hybris/bin/platform/build.xml
              '''
            }
          }
   
                  stage ('Hybris Code Compilation') {
        steps {
            echo "====== Running Hybris Targets - customize, clean and all ======"          
              sh '''
              cd ${WORKSPACE}/Code/hybris/bin/platform/
              . ./setantenv.sh
              ANT_OPTS="-XX:-UseSplitVerifier -XX:MaxPermSize=512M"
              ant clean all'''
          }
        }
      
                  stage ('UI Code Compilation') {
        when {
          expression { return UI_Build ==~ /(?i)(Y|YES|T|TRUE|ON|RUN)/ }
          }       
        steps {
            echo "====== Running UI Code Compilation and Build using Node/NPM ======"
            sh '''
            cd Code/hybris/bin/custom/co/costorefront/web
            #mkdir homebrew && curl -L https://github.com/Homebrew/brew/tarball/master | tar xz --strip 1 -C homebrew
            #homebrew/bin/brew install fontforge ttfautohint
            npm install -g grunt-cli
            npm install
            npm run start
            grunt
            npm test
            '''
        }
        post {
                always {
                    publishHTML([allowMissing: false, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'Code/hybris/bin/custom/co/costorefront/web/webroot/WEB-INF/_ui-src/lintReports', reportFiles: 'eslint-report.html', reportName: 'esLint Report', reportTitles: ''])
                    publishHTML([allowMissing: false, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'Code/hybris/bin/custom/co/costorefront/web/webroot/WEB-INF/_ui-src/testReports', reportFiles: 'index.html', reportName: 'Jest Report', reportTitles: ''])
                }
            }
      }
 
                stage ('UI Sonar') {
                  /*  when {
          expression { return Sonar ==~ /(?i)(Y|YES|T|TRUE|ON|RUN)/ }
          } */
            steps {
              echo "====== Current WORKSPACE Directory is ${env.WORKSPACE} ======="
              echo "====== Copying Local Properties & updating properties for CI Environments ======"
                sh '''
                  cd Code/hybris/bin/custom/co/costorefront/web
                  /opt/sonar-runner-2.4/bin/sonar-runner
                '''
              }
            post {
                always {
                    publishHTML([allowMissing: false, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'Code/hybris/bin/custom/co/costorefront/web/webroot/WEB-INF/_ui-src/lintReports', reportFiles: 'eslint-report.html', reportName: 'esLint Report', reportTitles: ''])
                   
                }
            }
            }
    stage ('Hybris JUNIT tenant Initialization') {
                /*  when {
          expression { return JunitTest ==~ /(?i)(Y|YES|T|TRUE|ON|RUN)/ }
          } */ 
          steps {
              echo "====== Running Hybris Targets - customize, clean and all ======"          
                sh '''
                cd ${WORKSPACE}/Code/hybris/bin/platform/
                . ./setantenv.sh
                ANT_OPTS="-XX:-UseSplitVerifier -XX:MaxPermSize=512M"
                ant yunitinit -Dmaven.update.dbdrivers=false '''
            }
      }
  
    stage ('Hybris JUNIT') {
                /*  when {
          expression { return JunitTest ==~ /(?i)(Y|YES|T|TRUE|ON|RUN)/ }
          }  */
          steps {
              echo "====== Running Hybris Targets - JUNIT ======"        
              sh '''
              cd ${WORKSPACE}/Code/hybris/bin/platform/
                . ./setantenv.sh
                ANT_OPTS="-XX:-UseSplitVerifier -XX:MaxPermSize=512M"
                ant unittests -Dtestclasses.packages=se.clasohlson.*
              '''
            }
          post {
          always {
              jacoco classPattern: '**/custom/**/classes', exclusionPattern: '**/jalo/**/*.class,**/constants/**/*.class,**/dto/**/*.class,**/*DTO.class,**/integ/webservices/**/*.class,**/*Standalone.class,**/gensrc/**/*.class,**/cocmscockpit/**/*.class,**/cocscockpit/**/*.class,**/coproductcockpit/**/*.class', sourcePattern: '**/custom/**/src'
              junit 'Code/hybris/log/junit/*.xml'
              }
          }   
        }
  
    stage ('Sonar Analysis') {
                /*  when {
          expression { return Sonar ==~ /(?i)(Y|YES|T|TRUE|ON|RUN)/ }
          }  */
          steps {
            echo "====== Running Sonar Analysis ======"
              sh '''
              cd ${WORKSPACE}/Code/hybris/bin/platform
              . ./setantenv.sh
              ANT_OPTS="-Xmx4G -noverify" ant sonarcheck -Dmaven.update.dbdrivers=false
              '''
          }
          post {
          success {
          echo "====== Sonar Job is Successful ========"
          }
      }
    }
    stage ('Quality Check For UI') {
    /*    when {
          expression { return Sonar ==~ /(?i)(Y|YES|T|TRUE|ON|RUN)/ }
          } */
        steps {
            echo "Sonar Quality Check for UI"
            sh '''
            QGSTATUS=`curl http://10.253.10.190:9000/api/qualitygates/project_status?projectKey=ClasOhlsonUX | jq '.projectStatus.status' | tr -d '"'`
            if [ "$QGSTATUS" = "OK" ]
            then
            exit 0
            elif [ "$QGSTATUS" = "ERROR" ]
            then
            exit 1
            fi
            '''
        }
    }
    stage ('Quality Check for Hybris') {
     /*   when {
          expression { return Sonar ==~ /(?i)(Y|YES|T|TRUE|ON|RUN)/ }
          } */
        steps {
           echo "Sonar Quality Check for Hybris"
            sh '''
            sleep 180
            QGSTATUS=`curl http://10.253.10.190:9000/api/qualitygates/project_status?projectKey=CO_Upgrade | jq '.projectStatus.status' | tr -d '"'`
            if [ "$QGSTATUS" = "OK" ]
            then
            exit 0
            elif [ "$QGSTATUS" = "ERROR" ]
            then
            exit 1
            fi
            '''
        }
    }
     stage ('Create TAG for Code Repositories') {
               steps {
                 echo "====== Running Code Tag Creation ======="         
                  sh '''set -x
                                                                  DATE=$(date +%d%b%y)
                                                                Code_Build_Tag=${BRANCH}-${DATE}-${BUILD_NUMBER}
                                                                  git tag -l ${Code_Build_Tag}
                  sleep 10
                  git tag -a -f -m "Tagging on ${BRANCH} : ${Code_Build_Tag}" ${Code_Build_Tag}
                  sleep 10
                  pwd
                  git push origin ${Code_Build_Tag}
                  echo "${Code_Build_Tag}" > Code_tag_info.txt
                  '''
                 echo "${BUILD_NUMBER}"
                 //echo "${Code_Build_Tag}"
                 script {
                            // trim removes leading and trailing whitespace from the string
                            Code_Tag_Name = readFile('Code_tag_info.txt').trim()
                  }
                 echo "${Code_Tag_Name}"
            }
          }
                 stage ('Hybris Production - ZIP Creation') {
        when {
          expression { return Artifact ==~ /(?i)(Y|YES|T|TRUE|ON|RUN)/ }
          }
        steps {
                  echo "====== Running Hybris Zip Creation Target - PRODUCTION ======="         
                  sh '''
                  cd ${WORKSPACE}/Code/hybris/bin/platform && . ./setantenv.sh
                  export ANT_OPTS="-noverify -Xms2G -Xmx2G"
                  ant build_details
                  ant production -Dmaven.update.dbdrivers=false'''
                  echo "====== Running Hybris Config Files Archiving ======"
                  sh '''
                  cd ${WORKSPACE}/Code/hybris
                  zip -r ${WORKSPACE}/Code/hybris/temp/hybris/hybrisServer/hybrisServer-config.zip config -x "**/.git**"
                  '''
            }
      post {
            always {
            echo "====== Archiving Artifacts ========"
            archiveArtifacts artifacts: 'Code/hybris/temp/hybris/hybrisServer/*.zip, Code/hybris/bin/platform/temp/hybris/hybrisServer/buildversion.txt', caseSensitive: false, defaultExcludes: false
            }
        }
    }
                 /* stage ('Create TAG for Code Repositories') {
        when {
          expression { return CODE_TAG_CREATION ==~ /(?i)(Y|YES|T|TRUE|ON|RUN)/ }
          }
        steps {
                  echo "====== Running Code Tag Creation ======="         
                  sh '''set -x
                  TAG_DIRECTORY=/app/jenkins/BUILD_TAG/hybris/US
                  if [ -f $TAG_DIRECTORY/${BRANCH}/tag_num.txt ]
                  then
                    PREV_NUM=`cat $TAG_DIRECTORY/${BRANCH}/tag_num.txt`
                    NEW_NUM=`expr $PREV_NUM + 1`
                  else
                      mkdir -p $TAG_DIRECTORY/${BRANCH}
                      echo 0 > $TAG_DIRECTORY/${BRANCH}/tag_num.txt
                      PREV_NUM=`cat $TAG_DIRECTORY/${BRANCH}/tag_num.txt`
                      NEW_NUM=`expr $PREV_NUM + 1`
                  fi
 
                  echo $NEW_NUM > $TAG_DIRECTORY/${BRANCH}/tag_num.txt
                  BUILDNUM=`cat $TAG_DIRECTORY/${BRANCH}/tag_num.txt`
                  TAG=${BRANCH}-CODE-TAG-$BUILDNUM
 
                  ## Below is done to convert the TAG which can be used a lable for Google Image, as Google Image does not support Upper Case and '.'
                  TAG=`echo ${TAG} | sed -E "s/[^a-zA-Z0-9-]/-/g" | cut -c 1-62 | awk '{print tolower($0)}'`
                  if [ "${TAG: -1}" == '-' ];then TAG=`echo ${TAG} | sed 's/.$//'`; fi
 
                  echo "${TAG}" > CODE_TAG_INFO.txt
 
                  echo "created on `date` with tag ${TAG} and branch ${BRANCH} " >> $TAG_DIRECTORY/${BRANCH}/tag_info.txt
                  #Code Build TAG
                  git tag -l ${TAG}
                  sleep 10
                  git tag -a -f -m "Tagging on ${BRANCH} : ${TAG}" ${TAG}
                  sleep 10
                  git push ssh://git@bitbucket.keurig.com:7999/gmcr/us-b2c.git ${TAG}
 
                  echo "$TAG" > CODE_TAG_INFO.txt
                  '''
                  script {
                            // trim removes leading and trailing whitespace from the string
                            CODE_TAG_NAME = readFile('CODE_TAG_INFO.txt').trim()
                  }
                  echo "${CODE_TAG_NAME}"
            }
          }
                    stage ('Create TAG Config Repositories') {
        when {
          expression { return CONF_TAG_CREATION ==~ /(?i)(Y|YES|T|TRUE|ON|RUN)/ }
          }
        steps {
                  echo "====== Running Config Tag Creation ======="         
                  sh '''set -x
                  TAG_DIRECTORY=/app/jenkins/BUILD_TAG/hybris/US
                  if [ -f $TAG_DIRECTORY/${CONFIG_BRANCH}/tag_num.txt ]
                  then
                    PREV_NUM=`cat $TAG_DIRECTORY/${CONFIG_BRANCH}/tag_num.txt`
                    NEW_NUM=`expr $PREV_NUM + 1`
                  else
                      mkdir -p $TAG_DIRECTORY/${CONFIG_BRANCH}
                      echo 0 > $TAG_DIRECTORY/${CONFIG_BRANCH}/tag_num.txt
                      PREV_NUM=`cat $TAG_DIRECTORY/${CONFIG_BRANCH}/tag_num.txt`
                      NEW_NUM=`expr $PREV_NUM + 1`
                  fi
 
                  echo $NEW_NUM > $TAG_DIRECTORY/${CONFIG_BRANCH}/tag_num.txt
                  BUILDNUM=`cat $TAG_DIRECTORY/${CONFIG_BRANCH}/tag_num.txt`
 
                  ## Below is done to convert the TAG which can be used a lable for Google Image, as Google Image does not support Upper Case and '.'
                  CONFIG_TAG=${CONFIG_BRANCH}-CONFIG-TAG-$BUILDNUM
                  CONFIG_TAG=`echo ${CONFIG_TAG} | sed -E "s/[^a-zA-Z0-9-]/-/g" | cut -c 1-62 | awk '{print tolower($0)}'`
                  if [ "${CONFIG_TAG: -1}" == '-' ];then CONFIG_TAG=`echo ${CONFIG_TAG} | sed 's/.$//'`; fi
 
                  echo "${CONFIG_TAG}" >> CONFIG_TAG_INFO.txt
 
                  echo "created on `date` with config tag ${CONFIG_TAG} and branch ${CONFIG_BRANCH} " >> $TAG_DIRECTORY/${CONFIG_BRANCH}/tag_info.txt
                  #Config Build TAG
                  cd Hybris_Config/
                  git tag -l ${CONFIG_TAG}
                  sleep 10
                  git tag -a -f -m "Tagging on ${CONFIG_BRANCH} : ${CONFIG_TAG}" ${CONFIG_TAG}
                  sleep 10
                  git push ssh://git@bitbucket.keurig.com:7999/gmcr/configuration.git ${CONFIG_TAG}
 
                  echo "${CONFIG_TAG}" >> CONFIG_TAG_INFO.txt
                  '''
                  script {
                            // trim removes leading and trailing whitespace from the string
                            CONFIG_TAG_NAME = readFile('CONFIG_TAG_INFO.txt').trim()
                  }
                  echo "${CONFIG_TAG_NAME}"
            }
      }
  }
/*           post {
        success {
        echo "====== Job is Successful ========"
        }
        always {
          addShortText background: 'yellow', borderColor: '', color: '', link: '', text: "${CODE_TAG_NAME}"
          addShortText background: 'yellow', borderColor: '', color: '', link: '', text: "${CONFIG_TAG_NAME}"
          createSummary icon: 'star-gold.gif', id: '', text: "HYBRIS CODE TAG: ${CODE_TAG_NAME}"
          createSummary icon: 'star-gold.gif', id: '', text: "HYBRIS CONFIG TAG: ${CONFIG_TAG_NAME}"
        }
    }*/
    
    stage ('Deploy On environment'){
        when {
          expression { return Deploy ==~ /(?i)(Y|YES|T|TRUE|ON|RUN)/ }
          }
        steps {
           build job: "${environment}",
           parameters: [
               string(name: 'HybrisRoot', value: '/opt'),
               string(name: 'Build_Job_Name', value: 'Hybirs_CI'),
               string(name: 'Build_Num', value: "${BUILD_NUMBER}"),
               string(name: 'environment', value: "${environment}"),
               booleanParam(name: 'ExecuteWebDeploy', value: false),
               booleanParam(name: 'ExecuteAppDeploy', value: true)
               ]
        }
    }
   
 /*   stage ('CATS Run') {
        when {
          expression { return Deploy ==~ /(?i)(Y|YES|T|TRUE|ON|RUN)/ }
          }
        steps {
            echo "CATS Run on "
            build job: 'cats_test', parameters: [string(name: 'environment', value: "${environment}")]
        }
    } */
}
post {
    always {
         emailext attachLog: true, body: '${JELLY_SCRIPT,template="static-analysis"}', recipientProviders: [developers(), culprits(), brokenTestsSuspects(), upstreamDevelopers()], subject: 'Build | $PROJECT_NAME | $BUILD_NUMBER | $BUILD_STATUS | Deployed on $environment ', to: 'vaibhav.saxena1@publicissapient.com, MSO-ClasOhlsonInfraSupport@publicissapient.com, MSO-ClasOhlsonAppSupport@publicissapient.com'
         createSummary icon: 'star-gold.gif', id: '', text: "HYBRIS CODE TAG: ${Code_Tag_Name}"
         addShortText background: 'yellow', borderColor: '', color: '', link: '', text: "${Code_Tag_Name}"
        script {
          DATE_TAG = java.time.LocalDate.now()
          DATETIME_TAG = java.time.LocalDateTime.now()
            }
            sh "echo ${DATETIME_TAG}"
          sh script: "echo 'date=${DATETIME_TAG} build_no=${BUILD_ID} Job_name=_${env.JOB_NAME} build_status=${currentBuild.currentResult}' >> /opt/jenkins/file.txt"
     
    }
   
       
    unstable {
        slackSend channel: '#upgrade-track-all', color: 'warning', message: " Build Unstable '${JOB_NAME}' '${BUILD_NUMBER}' (<'${BUILD_URL}' | Open>)", tokenCredentialId: 'slack'
    }
    failure {
        slackSend channel: '#upgrade-track-all', color: 'danger', message: " Build Failed '${JOB_NAME}' '${BUILD_NUMBER}' (<'${BUILD_URL}' | Open>)", tokenCredentialId: 'slack'
    }
    success {
        slackSend channel: '#upgrade-track-all', color: 'good', message: " Build Success '${JOB_NAME}' '${BUILD_NUMBER}' (<'${BUILD_URL}' | Open>)", tokenCredentialId: 'slack'
    }
    }
 
}