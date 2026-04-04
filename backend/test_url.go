package main

import (
	"fmt"
	"github.com/kkdai/youtube/v2"
)

func main() {
	client := youtube.Client{}
	video, err := client.GetVideo("dQw4w9WgXcQ")
	if err != nil {
		fmt.Println("Err:", err)
		return
	}
	formats := video.Formats.Type("audio")
	if len(formats) > 0 {
		url, err := client.GetStreamURL(video, &formats[0])
		fmt.Println(url, err)
	} else {
		fmt.Println("No audio formats")
	}
}
