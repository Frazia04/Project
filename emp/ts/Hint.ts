//one Hint that saves the different languages
export default class Hint {
    private german: string;
    private english: string;

    constructor(english: string, german: string) {
        this.english = english;
        this.german = german;
    }

    getLanguage(language: string): string {
        if (language === "english") {
            return this.english
        }
        else if (language === "german" && this.german) {
            return this.german;
        }
        else {
            //defaults to english if language not supported
            return this.english;
        }
    }
}