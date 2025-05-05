The Android App for speech to text recognition:
1. Collect speech/acoustic signals
2. Transformation from speech to text using speech recognition library
3. Display the translated text in the end Android device

output:
1. Retrieve the data and send transcribed results to textview.
2. Playback audio

Challenges:
Cannot use 2 microphones to record and transcribe at same time.
Solution: 
Used the third party API deepgram API to address this issue.

How to run App:
1) Clone the project
   git clone https://github.com/Tej619/Text_to_Speech.git
   
2) Make changes in MainActivity.java file give your own DEEPGRAM_API_KEY on line no. 54
   
3) Load the project in Android Studio Code Wait for some time to load the application with all the gradle dependencies.

4) Run the emulator and then run the application.

4) How to run the app you can watch the attached demo_video.mp4 
   https://github.com/user-attachments/assets/c1f7359b-16db-40dd-8df7-ce9f8ed5b836
