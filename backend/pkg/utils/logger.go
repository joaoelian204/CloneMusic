package utils

import (
	"log"
	"os"
)

var (
	infoLogger  = log.New(os.Stdout, "INFO: ", log.Ldate|log.Ltime|log.Lshortfile)
	errorLogger = log.New(os.Stderr, "ERROR: ", log.Ldate|log.Ltime|log.Lshortfile)
)

func LogInfo(msg string) {
	infoLogger.Println(msg)
}

func LogError(msg string, err error) {
	if err != nil {
		errorLogger.Printf("%s - Err: %v\n", msg, err)
	} else {
		errorLogger.Println(msg)
	}
}
