import Hint from "./Hint";
import Trie from "./Trie";

export default interface LanguageInterface {
    //gets trie containing keyword for hints
    getKeywordsTrie(): Trie;

    //just passes through to the internal dictionary
    getHint(keyword: string): Hint | undefined;

    //takes string and outputs html string with coloring added
    color(input: string): string;
}