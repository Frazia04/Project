//https://medium.com/@suyashtiwari1798/trie-typescript-4d88be3ec561
class Trie {

    private map: { [key: string]: Trie } = {};
    private isWord: boolean = false;
    private longestKeywordLength: number = 0;

    constructor(words?: IterableIterator<string>) {
        if (words) {
            for (let word of words) {
                if (word.length > this.longestKeywordLength) {
                    this.longestKeywordLength = word.length;
                }
                this.add(word, 0, this);
            }
        }
    }

    public search(word: string): number {
        return this.find(word, 0, this);
    }

    public getLongestLength() {
        return this.longestKeywordLength;
    }

    private add(word: string, index: number, letterMap: Trie): void {
        if (index === word.length) {
            letterMap.isWord = true;
            return;
        }

        if (!letterMap.map[word.charAt(index)]) {
            letterMap.map[word.charAt(index)] = new Trie();
        }

        return this.add(word, index + 1, letterMap.map[word.charAt(index)]);
    }

    //returns the length of a found word, or -1 if word wasn't found
    private find(word: string, index: number, letterMap: Trie): number {
        if (!letterMap) {
            return -1;
        }

        else if (letterMap.isWord) {
            let recur =
                this.find(
                    word,
                    index + 1,
                    letterMap.map[word.charAt(index)]
                );

            if (recur > -1) {
                return recur;
            }
            else {
                return index;
            }
        }

        else if (letterMap.map[word[index]]) {
            return this.find(
                word,
                index + 1,
                letterMap.map[word.charAt(index)]
            );
        }

        return -1;
    }

}

export default Trie;