pipeline {
  agent any

  options {
    timestamps()
    disableConcurrentBuilds()
  }

  parameters {
    string(
      name: 'DEPLOY_VERSION',
      defaultValue: '',
      description: '待发布版本。留空时优先使用 Tag，非 Tag 构建使用根 POM 的 revision。'
    )
    string(
      name: 'ALIYUN_MAVEN_CREDENTIALS_ID',
      defaultValue: 'aliyun-maven',
      description: 'Jenkins 中保存阿里云 Maven 用户名和密码的 Username/Password 凭据 ID。'
    )
    booleanParam(
      name: 'SKIP_TESTS',
      defaultValue: false,
      description: '紧急补发时可跳过测试；正式版本默认执行测试。'
    )
  }

  environment {
    MAVEN_OPTS = '-Xms512m -Xmx2048m -Dfile.encoding=UTF-8'
  }

  stages {
    stage('检出') {
      steps {
        checkout scm
      }
    }

    stage('解析发布版本') {
      steps {
        script {
          String requestedVersion = params.DEPLOY_VERSION.trim()
          String tagVersion = env.TAG_NAME?.trim()
          String resolvedVersion

          if (requestedVersion) {
            resolvedVersion = requestedVersion
          } else if (tagVersion) {
            resolvedVersion = tagVersion.replaceFirst('^v', '')
          } else {
            resolvedVersion = sh(
              script: 'mvn -q -DforceStdout help:evaluate -Dexpression=revision',
              returnStdout: true
            ).trim()
          }

          if (!resolvedVersion || resolvedVersion.contains('${')) {
            error('无法解析发布版本，请通过 DEPLOY_VERSION 或版本 Tag 显式指定。')
          }

          env.RESOLVED_DEPLOY_VERSION = resolvedVersion
          currentBuild.displayName = "#${env.BUILD_NUMBER} ${resolvedVersion}"
          echo "准备发布 Maven 版本: ${resolvedVersion}"
        }
      }
    }

    stage('部署到阿里云 Maven 私有仓库') {
      steps {
        withCredentials([
          usernamePassword(
            credentialsId: "${params.ALIYUN_MAVEN_CREDENTIALS_ID}",
            usernameVariable: 'ALIYUN_MAVEN_USERNAME',
            passwordVariable: 'ALIYUN_MAVEN_PASSWORD'
          )
        ]) {
          sh '''
            set +x
            SETTINGS_FILE="$(mktemp)"
            trap 'rm -f "$SETTINGS_FILE"' EXIT

            cat > "$SETTINGS_FILE" <<'SETTINGS_XML'
            <?xml version="1.0" encoding="UTF-8"?>
            <settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
              <servers>
                <server>
                  <id>2624322-release-6F6h6R</id>
                  <username>${env.ALIYUN_MAVEN_USERNAME}</username>
                  <password>${env.ALIYUN_MAVEN_PASSWORD}</password>
                </server>
                <server>
                  <id>2624322-snapshot-3EoOv3</id>
                  <username>${env.ALIYUN_MAVEN_USERNAME}</username>
                  <password>${env.ALIYUN_MAVEN_PASSWORD}</password>
                </server>
              </servers>
            </settings>
            SETTINGS_XML

            TEST_ARGUMENT=''
            if [ "$SKIP_TESTS" = 'true' ]; then
              TEST_ARGUMENT='-DskipTests'
            fi

            mvn -B -U -s "$SETTINGS_FILE" \
              -Drevision="$RESOLVED_DEPLOY_VERSION" \
              $TEST_ARGUMENT \
              deploy
          '''
        }
      }
    }
  }

  post {
    success {
      echo "阿里云 Maven 私有仓库发布成功: ${env.RESOLVED_DEPLOY_VERSION}"
    }
    failure {
      echo "阿里云 Maven 私有仓库发布失败: ${env.RESOLVED_DEPLOY_VERSION ?: 'unknown'}"
    }
  }
}
