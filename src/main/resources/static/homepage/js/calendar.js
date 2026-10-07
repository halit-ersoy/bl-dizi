// calendar.js
export function initCalendar() {
    // Find the Takvim link
    const navLinks = document.querySelectorAll('a.nav-link');
    let takvimLink;

    navLinks.forEach(link => {
        if (link.textContent.trim() === 'Takvim') {
            takvimLink = link;
        }
    });

    if (!takvimLink) return;

    let calendarModal = document.getElementById('calendar-modal');
    if (!calendarModal) {
        calendarModal = document.createElement('div');
        calendarModal.className = 'calendar-modal-overlay';
        calendarModal.id = 'calendar-modal';
        calendarModal.style.display = 'none';
        calendarModal.innerHTML = `
            <div class="calendar-modal">
                <button class="close-modal" id="close-calendar-modal">
                    <i class="fas fa-times"></i>
                </button>
                <div class="calendar-header">
                    <h2>Takvim</h2>
                </div>
                <div class="weekly-calendar"></div>
            </div>
        `;
        document.body.appendChild(calendarModal);
    }

    const closeCalendarButton = document.getElementById('close-calendar-modal');

    takvimLink.addEventListener('click', (e) => {
        e.preventDefault();
        loadCalendarData();
        calendarModal.style.display = 'flex';
        document.body.style.overflow = 'hidden';
    });

    if (closeCalendarButton) {
        closeCalendarButton.addEventListener('click', () => {
            calendarModal.style.display = 'none';
            document.body.style.overflow = '';
        });
    }

    // Close when clicking outside the modal
    calendarModal.addEventListener('click', (e) => {
        if (e.target === calendarModal) {
            calendarModal.style.display = 'none';
            document.body.style.overflow = '';
        }
    });

    // Add keyboard support for accessibility
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && calendarModal.style.display === 'flex') {
            calendarModal.style.display = 'none';
            document.body.style.overflow = '';
        }
    });

    function loadCalendarData() {
        const cachedCalendar = sessionStorage.getItem('calendarData');
        if (cachedCalendar) {
            try {
                const calendarData = JSON.parse(cachedCalendar);
                createDrawerStructure(calendarData);
                return;
            } catch (e) {
                console.error('Failed to parse cached calendar data:', e);
            }
        }

        fetch('/api/calendar')
            .then(response => response.json())
            .then(calendarData => {
                sessionStorage.setItem('calendarData', JSON.stringify(calendarData));
                // Setup the drawer structure
                createDrawerStructure(calendarData);
            })
            .catch(error => console.error('Error loading calendar data:', error));
    }

    function createDrawerStructure(calendarData) {
        const weeklyCalendar = document.querySelector('.weekly-calendar');
        if (!weeklyCalendar) return;

        // Clear previous content
        weeklyCalendar.innerHTML = '';

        const dayNames = {
            'monday': 'Pazartesi',
            'tuesday': 'Salı',
            'wednesday': 'Çarşamba',
            'thursday': 'Perşembe',
            'friday': 'Cuma',
            'saturday': 'Cumartesi',
            'sunday': 'Pazar'
        };

        const daysOrder = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'];
        const daysShort = ['sunday', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday'];
        const userNow = new Date();
        const todayDayName = daysShort[userNow.getDay()];

        // Data processing: Shift events to user's local day/time
        // We assume database 'showTime' is in Turkey Time (UTC+3)
        const localCalendarData = {
            monday: [], tuesday: [], wednesday: [], thursday: [], friday: [], saturday: [], sunday: []
        };

        Object.keys(calendarData).forEach(dbDay => {
            const shows = calendarData[dbDay] || [];
            shows.forEach(show => {
                // Parse "HH:mm" in TRT (UTC+3)
                const [hours, minutes] = show.time.split(':').map(Number);

                // Create a date object representing specific day in TRT
                // Find index of dbDay in daysShort to get correct JS day
                const dbDayIdx = daysShort.indexOf(dbDay);

                // Construct a date in user's locale but representing the TRT time
                // A reliable way: Create a UTC date and shift by 3 hours
                const trDate = new Date();
                // Set to the correct day of week relative to now
                const currentDay = trDate.getDay();
                const distance = dbDayIdx - currentDay;
                trDate.setDate(trDate.getDate() + distance);
                trDate.setHours(hours - 3, minutes, 0, 0); // Convert TRT (UTC+3) to UTC

                // Now trDate is the exact UTC moment the show airs.
                // Fetch user's local day and time from this UTC moment.
                const localDayName = daysShort[trDate.getDay()];
                const localHours = String(trDate.getHours()).padStart(2, '0');
                const localMinutes = String(trDate.getMinutes()).padStart(2, '0');
                const localTimeStr = `${localHours}:${localMinutes}`;

                if (localCalendarData[localDayName]) {
                    localCalendarData[localDayName].push({
                        ...show,
                        time: localTimeStr,
                        originalTime: show.time // Keep for reference if needed
                    });
                }
            });
        });

        // Create drawer for each day in specific order
        daysOrder.forEach(day => {
            const shows = localCalendarData[day] || [];
            // Sort by time
            shows.sort((a, b) => a.time.localeCompare(b.time));

            const dayEl = document.createElement('div');
            dayEl.className = 'calendar-day';
            dayEl.dataset.day = day;

            // Create header (clickable part)
            const header = document.createElement('div');
            header.className = 'calendar-day-header';
            header.innerHTML = `
                <h3>${dayNames[day]} <span class="show-count">${shows.length}</span></h3>
                <i class="fas fa-chevron-down"></i>
            `;

            // Create content container
            const content = document.createElement('div');
            content.className = 'calendar-day-content';
            content.id = `${day}-shows`;

            if (shows.length === 0) {
                content.innerHTML = '<p class="no-shows">Bugün için program yok</p>';
            } else {
                shows.forEach(show => {
                    const showEl = document.createElement('div');
                    showEl.className = 'calendar-show';
                    showEl.innerHTML = `
                        <div class="calendar-show-info">
                            <h4 class="calendar-show-title">${show.title}</h4>
                            <p class="calendar-show-episode">${show.episode}</p>
                            <p class="calendar-show-time">${show.time}</p>
                        </div>
                    `;
                    content.appendChild(showEl);
                });
            }

            dayEl.appendChild(header);
            dayEl.appendChild(content);
            weeklyCalendar.appendChild(dayEl);

            // Add click event
            header.addEventListener('click', function () {
                toggleDrawer(day);
            });
        });

        // Open today's drawer by default
        setTimeout(() => {
            toggleDrawer(todayDayName);
        }, 100);
    }

    function toggleDrawer(dayToOpen) {
        const allDays = document.querySelectorAll('.calendar-day');

        // Close all drawers first
        allDays.forEach(day => {
            const header = day.querySelector('.calendar-day-header');
            const content = day.querySelector('.calendar-day-content');
            const icon = header.querySelector('i');

            day.classList.remove('active');
            content.style.maxHeight = null;
            icon.className = 'fas fa-chevron-down';
        });

        // Open the selected drawer
        const selectedDay = document.querySelector(`.calendar-day[data-day="${dayToOpen}"]`);
        if (selectedDay) {
            const header = selectedDay.querySelector('.calendar-day-header');
            const content = selectedDay.querySelector('.calendar-day-content');
            const icon = header.querySelector('i');

            selectedDay.classList.add('active');
            content.style.maxHeight = content.scrollHeight + 'px';
            icon.className = 'fas fa-chevron-up';
        }
    }
}