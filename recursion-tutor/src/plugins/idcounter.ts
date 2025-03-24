import { defineStore } from 'pinia';

export const idCounterStore = defineStore('idCounter', {
    state: () => ({
        counter: 1
    }),
    actions: {
        getNextId(): number {
            const nextId = this.counter;
            this.counter++;
            return nextId;
        },
        resetCounter() {
            this.counter = 1;
        }
    }

})