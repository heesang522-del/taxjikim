document.addEventListener("DOMContentLoaded", () => {
    const grid=document.querySelector("#post-grid");
    const sentinel = document.querySelector("#post-scroll-sentinel");
    const loading = document.querySelector("#post-loading");

    if(!grid || !sentinel || !loading) {
        return;
    }

    const contextPath = grid.dataset.contextPath;
    const sort = grid.dataset.sort;
    const keyword = grid.dataset.keyword;
    const totalPages = Number(grid.dataset.totalPages);

    function  getDefaultImage(title) {
        const postTitle = title ?? "";

        if (postTitle.includes("서울")) {
            return "seoul.svg";
        }

        if (postTitle.includes("부산")) {
            return "busan.svg";
        }

        if (postTitle.includes("제주")) {
            return "jeju.svg";
        }

        if (postTitle.includes("경주")) {
            return "gyeongju.svg";
        }

        if (postTitle.includes("강릉")) {
            return "gangneung.svg";
        }

        if (postTitle.includes("전주")) {
            return "jeonju.svg";
        }

        if (postTitle.includes("인천")) {
            return "incheon.svg";
        }

        if (postTitle.includes("속초")) {
            return "sokcho.svg";
        }

        return "zzanmat-default.jpg"
    }


    let nextPage = 2;
    let isLoading = false;

    if(nextPage > totalPages) {
        sentinel.hidden = true;
        return;
    }

    function appendPost(post) {
        const card = document.createElement("a");

        card.className = "zt-grid-card";
        card.href =
            contextPath +
            "/post-detail?postId=" +
            encodeURIComponent(post.postId);

        const image = document.createElement("img");

        const defaultImagePath =
            contextPath +
            "/assets/images/" +
            getDefaultImage(post.title);

        image.addEventListener(
            "error",
            () => {
                image.src = defaultImagePath;
            },
            {
                once: true
            }
        );

        if (post.thumbnailPath) {
            image.src = contextPath + post.thumbnailPath;
        } else {
            image.src = defaultImagePath;
        }

        image.alt = post.title ?? "";

        const overlay = document.createElement("span");

        overlay.className = "zt-grid-overlay";
        overlay.append(document.createTextNode(post.title ?? ""))

        const eyeIcon = document.createElement("i");

        eyeIcon.className = "bi bi-eye-fill ms-2 me-1";

        overlay.append(eyeIcon);
        overlay.append(
            document.createTextNode(String(post.viewCount ?? 0))
        );

        card.append(image);
        card.append(overlay);
        grid.append(card);
    }

    async function loadNextPage() {
        if (isLoading || nextPage > totalPages) {
            return;
        }

        isLoading = true;
        loading.hidden = false;

        try {
            const params = new URLSearchParams({
                sort: sort,
                keyword: keyword,
                page: String(nextPage)
            });

            const response = await fetch(
                contextPath + "/api/posts?" + params.toString()
            );

            if (!response.ok) {
                throw new Error("게시글을 불러오지 못했습니다.");
            }

            const data = await response.json();
            const pageData = data.data ?? data;

            pageData.posts.forEach(appendPost);

            nextPage++;

            if (!pageData.hasNext) {
                observer.disconnect();
                sentinel.hidden = true;
            }
        } catch (error) {
            console.error(error);
        } finally {
            isLoading = false;
            loading.hidden = true;
        }
    }

        const observer = new IntersectionObserver(
            (entries) => {
                if(entries[0].isIntersecting) {
                    loadNextPage();
                }
            },
            {
                rootMargin: "200px"
            }
        );

        observer.observe(sentinel);
    });
