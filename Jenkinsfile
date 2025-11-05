pipeline {
  agent any

  environment {
    APP_NAME    = "file-copy-system"
    DEPLOY_DIR  = "/opt/${APP_NAME}"
    JAR_NAME    = "File_Copy_System-0.0.1-SNAPSHOT.jar"
  }

  stages {
    stage('Checkout') {
      steps {
        git branch: 'main',
            credentialsId: 'github-token',
            url: 'https://github.com/subhani07860/File_Copy_System.git'
      }
    }

    stage('Build') {
      steps {
        sh 'mvn -B clean package -DskipTests=false'
      }
      post {
        always {
          archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
        }
      }
    }

    stage('Unit Tests') {
      steps {
        sh 'mvn test'
      }
    }

    stage('Prepare Deploy Dir') {
      steps {
        sh """
          sudo mkdir -p ${DEPLOY_DIR}
          sudo chmod 755 ${DEPLOY_DIR}
        """
      }
    }

    stage('Deploy') {
      steps {
        sh """
          sudo cp target/${JAR_NAME} ${DEPLOY_DIR}/app.jar
          sudo mkdir -p /var/log/${APP_NAME}
          sudo chown -R $(whoami):$(whoami) /var/log/${APP_NAME}
          sudo systemctl daemon-reload || true
          sudo systemctl restart ${APP_NAME}.service || sudo systemctl start ${APP_NAME}.service
        """
      }
    }
  }

  post {
    success {
      echo "✅ Pipeline succeeded! ${APP_NAME} deployed successfully."
    }
    failure {
      echo "❌ Pipeline failed. Check logs for details."
    }
  }
}

