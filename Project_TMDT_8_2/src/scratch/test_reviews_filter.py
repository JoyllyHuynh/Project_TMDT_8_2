import re
from bs4 import BeautifulSoup

def test_reviews_page(filepath, pagename):
    print(f"\n==================== TESTING {pagename} ====================")
    with open(filepath, 'r', encoding='utf-8') as f:
        html = f.read()
    
    soup = BeautifulSoup(html, 'html.parser')
    
    # 1. Check Filter Elements
    star_tabs = soup.find(id='starFilterTabs') or soup.find(class_='filter-tabs-bar')
    assert star_tabs is not None, "Missing star tabs bar"
    print("✓ Star filter tabs bar found")
    
    chips = star_tabs.find_all(class_='tab-chip')
    print(f"✓ Found {len(chips)} star filter tab chips")
    star_filters = [c.get('data-filter-star') for c in chips if c.get('data-filter-star')]
    print(f"  Chip values: {star_filters}")
    assert 'all' in star_filters and '5' in star_filters and 'with_photo' in star_filters and 'unreplied' in star_filters
    
    book_select = soup.find(id='reviewBookSelect')
    assert book_select is not None, "Missing #reviewBookSelect"
    book_options = [opt['value'] for opt in book_select.find_all('option')]
    print(f"✓ Found #reviewBookSelect with {len(book_options)} options: {book_options[:4]}...")
    assert 'One Piece' in book_options and 'Dragon Ball Super' in book_options
    
    date_select = soup.find(id='reviewDateSelect')
    assert date_select is not None, "Missing #reviewDateSelect"
    date_options = [opt['value'] for opt in date_select.find_all('option')]
    print(f"✓ Found #reviewDateSelect with {len(date_options)} options: {date_options}")
    assert 'today' in date_options and '7days' in date_options and '30days' in date_options
    
    search_input = soup.find(id='reviewSearchInput')
    assert search_input is not None, "Missing #reviewSearchInput"
    print("✓ Found #reviewSearchInput")
    
    badge = soup.find(id='reviewMatchCountBadge')
    assert badge is not None, "Missing #reviewMatchCountBadge"
    print(f"✓ Found #reviewMatchCountBadge: '{badge.text.strip()}'")
    
    empty_state = soup.find(id='noReviewsFound')
    assert empty_state is not None, "Missing #noReviewsFound"
    print("✓ Found #noReviewsFound empty state")
    
    # 2. Check Cards & Data Attributes
    cards = soup.find_all(class_='review-card')
    print(f"✓ Found {len(cards)} review cards")
    assert len(cards) >= 8, f"Expected at least 8 cards, found {len(cards)}"
    
    for i, card in enumerate(cards, 1):
        stars = card.get('data-stars')
        book = card.get('data-book')
        date = card.get('data-date')
        photo = card.get('data-has-photo')
        replied = card.get('data-replied')
        
        assert stars is not None, f"Card {i} missing data-stars"
        assert book is not None, f"Card {i} missing data-book"
        assert date is not None, f"Card {i} missing data-date"
        assert photo is not None, f"Card {i} missing data-has-photo"
        assert replied is not None, f"Card {i} missing data-replied"
        
        reviewer = card.find(class_='reviewer-info').find('h4').text.strip().split()[0]
        print(f"  Card {i}: {reviewer:12} | {stars}⭐ | {date} | {book[:25]}... | photo={photo} | replied={replied}")

    # 3. Simulate Combined Filtering
    # Test A: Filter 5 Stars
    five_stars = [c for c in cards if c.get('data-stars') == '5']
    print(f"✓ Simulation - 5 stars: {len(five_stars)} cards matched (Expect 4)")
    assert len(five_stars) == 4
    
    # Test B: Filter Book = 'One Piece'
    one_piece = [c for c in cards if 'one piece' in c.get('data-book', '').lower()]
    print(f"✓ Simulation - One Piece: {len(one_piece)} cards matched (Expect 1)")
    assert len(one_piece) == 1
    
    # Test C: Filter Date = 'today' (2026-09-24)
    today_cards = [c for c in cards if c.get('data-date') == '2026-09-24']
    print(f"✓ Simulation - Date Today: {len(today_cards)} cards matched (Expect 1)")
    assert len(today_cards) == 1
    
    # Test D: Filter Unreplied
    unreplied_cards = [c for c in cards if c.get('data-replied') == 'false']
    print(f"✓ Simulation - Unreplied: {len(unreplied_cards)} cards matched (Expect 4)")
    assert len(unreplied_cards) == 4

    print(f"==> ALL TESTS PASSED FOR {pagename}!\n")

if __name__ == '__main__':
    test_reviews_page(r'd:\hoc tap\TMDT\src\truong\html\index.html', 'index.html (#view-reviews)')
    test_reviews_page(r'd:\hoc tap\TMDT\src\truong\html\reviews.html', 'reviews.html (Standalone)')
