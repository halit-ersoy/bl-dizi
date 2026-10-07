import { initHero } from './hero.js';
import { initWeeklyBest } from './weeklyBest.js';
import { initFeaturedContent } from './featuredContent.js';
import { initSearch } from './search.js';

document.addEventListener('DOMContentLoaded', () => {
    initHero();
    initWeeklyBest();
    initFeaturedContent();
    initSearch();
});
