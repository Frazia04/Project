import LanguageInterface from './LanguageInterface';
import PythonLang from './languages/python.js';

//Supported languages, add new additions here and import files above
let supportedLangs = new Map<string, LanguageInterface>(
    [
        //add new languages here
        ["python", new PythonLang()]

    ]);

function insertHintsEMP(input: string, proLang: string, spoLang?: string): string {

    //default spoken languge is english if not given otherwise
    if (!spoLang) {
        spoLang = "english";
    }

    if (proLang) {
        proLang = proLang.toLocaleLowerCase();
    }
    else {
        return "No programming language given!";
    }
    spoLang = spoLang.toLocaleLowerCase();

    let langObj = supportedLangs.get(proLang);

    if (!langObj) {
        return "Sorry, language is not supported!";
    }

    let escString = "";

    for (let i = 0; i < input.length; i++) {
        //escape html
        if (input.charAt(i) === "&") {
            escString += "&amp;"
        }
        else if (input.charAt(i) === "<") {
            escString += "&lt;";
        }
        else if (input.charAt(i) === ">") {
            escString += "&gt;";
        }
        else if (input.charAt(i) === '"') {
            escString += "&quot;";
        }
        else if (input.charAt(i) === "'") {
            escString += "&#039;";
        }
        else {
            escString += input.charAt(i);
        }
    }
    //highlight the message
    input = langObj.color(escString);


    //benchmark addition
    // let output = "";
    // let before = input;
    // let startTime = new Date().getTime();
    // for (let amount = 0; amount < 1000; amount++) {
    //     input = before;

    //trie https://de.wikipedia.org/wiki/Trie
    //find keywords and place hints
    let output = "";

    for (let i = 0; i < input.length; i++) {
        //only search a string that is as long as longest keyword
        let searchString =
            input.substring(
                i,
                i + langObj.getKeywordsTrie().getLongestLength()
            );

        let foundLength = langObj.getKeywordsTrie().search(searchString);

        //if word was found, place hint in HTML
        if (foundLength > -1) {
            let word = searchString.substring(0, foundLength);

            output +=
                "<div class='tooltipEMP'>"
                + word
                + "<span class='tooltiptextEMP'>"
                + langObj.getHint(word)?.getLanguage(spoLang)
                + "</span></div>";

            //continue search after keyword
            i += foundLength - 1;
        }
        else {
            output += input.charAt(i);
        }
    }

    //benchmark addition
    // }
    // console.log(new Date().getTime() - startTime);

    return output;
}

export default insertHintsEMP;