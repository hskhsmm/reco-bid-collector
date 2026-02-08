document.addEventListener('DOMContentLoaded', function () {
    var btnStart = document.getElementById('btnStart');
    var btnStop = document.getElementById('btnStop');
    var startForm = document.getElementById('startForm');

    var pollingInterval = null;

    // 상태 한글 매핑
    var statusLabels = {
        'RUNNING': '수집 중',
        'COMPLETED': '완료',
        'FAILED': '실패',
        'STOPPED': '중지됨'
    };

    var typeLabels = {
        'INCREMENTAL': '새 공고만 수집',
        'FULL': '처음부터 전체 수집'
    };

    var badgeClassMap = {
        'CONSTRUCTION': 'badge-construction',
        'SERVICE': 'badge-service',
        'GOODS': 'badge-goods'
    };

    // 수집 시작
    startForm.addEventListener('submit', function (e) {
        e.preventDefault();

        var params = new URLSearchParams();
        params.append('keyword', document.getElementById('keyword').value);
        params.append('businessType', document.getElementById('businessType').value);
        params.append('type', document.getElementById('crawlType').value);
        params.append('async', 'true');

        btnStart.disabled = true;
        btnStart.textContent = '수집 중...';

        fetch('/api/scrape/start', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params.toString()
        })
        .then(function (res) {
            if (res.status === 409) {
                alert('이미 수집이 진행 중입니다.');
                btnStart.disabled = false;
                btnStart.textContent = '수집 시작';
                return;
            }
            return res.json();
        })
        .then(function (data) {
            if (data) {
                // 즉시 UI를 수집 중 상태로 전환
                btnStop.disabled = false;
                var badge = document.getElementById('statusBadge');
                badge.textContent = '수집 중';
                badge.className = 'badge status-running';
                // 크롤링 체크포인트 생성 대기 후 폴링 시작
                setTimeout(function () { startPolling(); }, 3000);
            }
        })
        .catch(function (err) {
            alert('수집 시작 실패: ' + err.message);
            btnStart.disabled = false;
            btnStart.textContent = '수집 시작';
        });
    });

    // 수집 중지
    btnStop.addEventListener('click', function () {
        if (!confirm('수집을 중지하시겠습니까?')) return;
        btnStop.disabled = true;

        fetch('/api/scrape/stop', { method: 'POST' })
            .then(function (res) { return res.json(); })
            .then(function () { fetchStatus(); })
            .catch(function (err) { alert('중지 실패: ' + err.message); });
    });

    // 상태 조회
    function fetchStatus() {
        fetch('/api/scrape/status')
            .then(function (res) { return res.json(); })
            .then(function (data) { updateUI(data); })
            .catch(function () {});
    }

    // 최근 공고 조회
    function fetchRecentNotices() {
        fetch('/api/notices/recent')
            .then(function (res) { return res.json(); })
            .then(function (data) { updateNoticesTable(data); })
            .catch(function () {});
    }

    // UI 업데이트
    function updateUI(data) {
        if (data.message === '기록 없음') {
            document.getElementById('statusBadge').textContent = '대기';
            document.getElementById('statusBadge').className = 'badge bg-secondary';
            btnStart.disabled = false;
            btnStart.textContent = '수집 시작';
            btnStop.disabled = true;
            return;
        }

        var status = data.status || '-';
        var badge = document.getElementById('statusBadge');
        badge.textContent = statusLabels[status] || status;
        badge.className = 'badge';

        switch (status) {
            case 'RUNNING':
                badge.classList.add('status-running');
                btnStart.disabled = true;
                btnStart.textContent = '수집 중...';
                btnStop.disabled = false;
                break;
            case 'COMPLETED':
                badge.classList.add('status-completed');
                btnStart.disabled = false;
                btnStart.textContent = '수집 시작';
                btnStop.disabled = true;
                stopPolling();
                break;
            case 'FAILED':
                badge.classList.add('status-failed');
                btnStart.disabled = false;
                btnStart.textContent = '수집 시작';
                btnStop.disabled = true;
                stopPolling();
                break;
            case 'STOPPED':
                badge.classList.add('status-stopped');
                btnStart.disabled = false;
                btnStart.textContent = '수집 시작';
                btnStop.disabled = true;
                stopPolling();
                break;
            default:
                badge.classList.add('bg-secondary');
                btnStart.disabled = false;
                btnStart.textContent = '수집 시작';
                btnStop.disabled = true;
        }

        document.getElementById('totalCollected').textContent = data.totalCollected || 0;
        document.getElementById('totalFailed').textContent = data.totalFailed || 0;
        document.getElementById('crawlTypeInfo').textContent = typeLabels[data.type] || data.type || '-';
        document.getElementById('lastPage').textContent = data.lastPage ? data.lastPage + ' 페이지' : '-';
        document.getElementById('startedAt').textContent = formatDateTime(data.startedAt);
        document.getElementById('completedAt').textContent = formatDateTime(data.completedAt);

        // 소요 시간 계산
        if (data.startedAt) {
            var start = new Date(data.startedAt);
            var end = data.completedAt ? new Date(data.completedAt) : new Date();
            var diffMs = end - start;
            var minutes = Math.floor(diffMs / 60000);
            var seconds = Math.floor((diffMs % 60000) / 1000);
            document.getElementById('elapsed').textContent = minutes + '분 ' + seconds + '초';
        } else {
            document.getElementById('elapsed').textContent = '-';
        }
    }

    // 최근 공고 테이블 업데이트
    function updateNoticesTable(data) {
        var countEl = document.getElementById('totalNoticeCount');
        if (countEl) countEl.textContent = data.totalCount || 0;

        var tbody = document.getElementById('recentNoticesBody');
        if (!tbody) return;

        if (!data.notices || data.notices.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">수집된 공고가 없습니다. 위에서 수집을 시작하세요.</td></tr>';
            return;
        }

        var html = '';
        data.notices.forEach(function (n) {
            html += '<tr>';
            html += '<td><a href="/notices/' + n.id + '" class="text-decoration-none">' + escapeHtml(n.bidNoticeName) + '</a></td>';
            html += '<td>';
            if (n.businessType) {
                var cls = badgeClassMap[n.businessTypeCode] || 'bg-secondary';
                html += '<span class="badge ' + cls + '">' + escapeHtml(n.businessType) + '</span>';
            }
            html += '</td>';
            html += '<td>';
            if (n.progressStatus) {
                html += '<span class="badge bg-info text-dark">' + escapeHtml(n.progressStatus) + '</span>';
            }
            html += '</td>';
            html += '<td>' + escapeHtml(n.orgName || '-') + '</td>';
            html += '<td>' + escapeHtml(n.noticeDt || '-') + '</td>';
            html += '<td>' + escapeHtml(n.bidEndDt || '-') + '</td>';
            html += '</tr>';
        });

        tbody.innerHTML = html;
    }

    function escapeHtml(text) {
        if (!text) return '';
        var div = document.createElement('div');
        div.appendChild(document.createTextNode(text));
        return div.innerHTML;
    }

    function formatDateTime(isoStr) {
        if (!isoStr) return '-';
        return isoStr.replace('T', ' ').substring(0, 19);
    }

    // 5초 간격 폴링 (상태 + 공고 목록)
    function startPolling() {
        if (pollingInterval) return;
        fetchStatus();
        fetchRecentNotices();
        pollingInterval = setInterval(function () {
            fetchStatus();
            fetchRecentNotices();
        }, 5000);
    }

    function stopPolling() {
        if (pollingInterval) {
            clearInterval(pollingInterval);
            pollingInterval = null;
        }
        // 최종 상태 한 번 더 갱신
        fetchRecentNotices();
    }

    // 초기 상태 로드
    fetch('/api/scrape/status')
        .then(function (res) { return res.json(); })
        .then(function (data) {
            updateUI(data);
            if (data.status === 'RUNNING') startPolling();
        })
        .catch(function () {});
});
