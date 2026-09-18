package study.activityfeed.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import study.activityfeed.domain.ActivityProjection;
import study.activityfeed.domain.Member;

import java.util.List;

public interface MemberRepository extends JpaRepository<Member, Long> {

    @Query(
            value = """
            select
                activity.id AS id,
                activity.type AS type,
                activity.content AS content,
                activity.created_at As createdAt
                from (
                    select
                        p.id AS id,
                        'POST' AS type,
                        p.title AS content,
                        p.created_at AS created_at
                    from posts p
                    where p.member_id = :memberId
                    
                    UNION ALL
                    
                    select
                        c.id AS id,
                        'COMMENT' AS type,
                        c.content AS content,
                        c.created_at AS created_at
                    from comments c 
                    where c.member_id = :memberId
                ) activity
             order by activity.created_at desc, activity.type desc, activity.id desc
             offset :offset rows fetch next :size rows only
             """,
            nativeQuery = true
    )
    List<ActivityProjection> findAllActivitiesById(
            @Param("memberId") Long memberId,
            @Param("offset") long offset,
            @Param("size") int size
    );

}
