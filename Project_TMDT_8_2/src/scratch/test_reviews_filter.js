const fs = require('fs');

function testHtmlFile(filePath, label) {
  console.log(`\n================ TESTING ${label} ================`);
  const content = fs.readFileSync(filePath, 'utf-8');

  // Check required IDs
  const requiredIds = [
    'starFilterTabs',
    'reviewBookSelect',
    'reviewDateSelect',
    'reviewSearchInput',
    'reviewMatchCountBadge',
    'noReviewsFound'
  ];

  requiredIds.forEach(id => {
    const hasId = content.includes(`id="${id}"`);
    console.log(`${hasId ? '✓' : '✗'} ID: #${id} -> ${hasId ? 'Found' : 'MISSING'}`);
    if (!hasId) throw new Error(`Missing #${id} in ${filePath}`);
  });

  // Check card count and data attributes
  const cardRegex = /<div class="review-card"([^>]*)>/g;
  let match;
  let count = 0;
  const cards = [];

  while ((match = cardRegex.exec(content)) !== null) {
    count++;
    const attrs = match[1];
    const stars = /data-stars="([^"]+)"/.exec(attrs)?.[1];
    const book = /data-book="([^"]+)"/.exec(attrs)?.[1];
    const date = /data-date="([^"]+)"/.exec(attrs)?.[1];
    const photo = /data-has-photo="([^"]+)"/.exec(attrs)?.[1];
    const replied = /data-replied="([^"]+)"/.exec(attrs)?.[1];

    cards.push({ stars, book, date, photo, replied });
    console.log(`  Card ${count}: ${stars}⭐ | Date: ${date} | Book: ${book.substring(0, 22)}... | Photo: ${photo} | Replied: ${replied}`);
  }

  console.log(`✓ Total Review Cards: ${count}`);
  if (count < 8) throw new Error(`Expected at least 8 cards, found ${count}`);

  // Test filter variations
  const fiveStars = cards.filter(c => c.stars === '5');
  console.log(`✓ 5-star cards: ${fiveStars.length} (Expected 4)`);
  if (fiveStars.length !== 4) throw new Error('5-star cards count mismatch');

  const onePiece = cards.filter(c => c.book.toLowerCase().includes('one piece'));
  console.log(`✓ One Piece cards: ${onePiece.length} (Expected 1)`);
  if (onePiece.length !== 1) throw new Error('One Piece cards count mismatch');

  const today = cards.filter(c => c.date === '2026-09-24');
  console.log(`✓ Today cards (2026-09-24): ${today.length} (Expected 1)`);
  if (today.length !== 1) throw new Error('Today cards count mismatch');

  const unreplied = cards.filter(c => c.replied === 'false');
  console.log(`✓ Unreplied cards: ${unreplied.length} (Expected 3)`);
  if (unreplied.length !== 3) throw new Error('Unreplied cards count mismatch');

  console.log(`===> ALL CHECKS PASSED FOR ${label}!`);
}

try {
  testHtmlFile('d:/hoc tap/TMDT/src/truong/html/index.html', 'index.html (#view-reviews)');
  testHtmlFile('d:/hoc tap/TMDT/src/truong/html/reviews.html', 'reviews.html (Standalone)');
  console.log('\n🌟 100% OF TESTS PASSED SUCCESSFULLY! 🌟\n');
} catch (err) {
  console.error('Test Failed:', err);
  process.exit(1);
}
