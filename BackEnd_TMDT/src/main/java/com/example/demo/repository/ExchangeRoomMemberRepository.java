package com.example.demo.repository;

import com.example.demo.model.ExchangeRoom;
import com.example.demo.model.ExchangeRoomMember;
import com.example.demo.model.User;
import com.example.demo.model.id.ExchangeRoomMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExchangeRoomMemberRepository extends JpaRepository<ExchangeRoomMember, ExchangeRoomMemberId> {

    List<ExchangeRoomMember> findByRoom(ExchangeRoom room);

    List<ExchangeRoomMember> findByUser(User user);

    boolean existsByRoomAndUser(ExchangeRoom room, User user);
}
