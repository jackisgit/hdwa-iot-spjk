package com.netsdk.lib.enumeration;


/** 
* @author 291189
* @description  物品类型 
* @date 2022/12/01 16:27:10
*/
public enum EM_INSIDE_OBJECT_TYPE {
/**
算法未识别物品
*/
EM_INSIDE_OBJECT_UNKNOWN(0,"算法未识别物品"),
/**
刀具
*/
EM_INSIDE_OBJECT_KNIFE(1,"刀具"),
/**
瓶装液体
*/
EM_INSIDE_OBJECT_BOTTLELIQUID(2,"瓶装液体"),
/**
枪支
*/
EM_INSIDE_OBJECT_GUN(3,"枪支"),
/**
雨伞
*/
EM_INSIDE_OBJECT_UMBRELLA(4,"雨伞"),
/**
手机
*/
EM_INSIDE_OBJECT_PHONE(5,"手机"),
/**
笔记本
*/
EM_INSIDE_OBJECT_NOTEBOOK(6,"笔记本"),
/**
充电宝
*/
EM_INSIDE_OBJECT_POWERBANK(7,"充电宝"),
/**
鞋子
*/
EM_INSIDE_OBJECT_SHOES(8,"鞋子"),
/**
杠子
*/
EM_INSIDE_OBJECT_ROD(9,"杠子"),
/**
金属
*/
EM_INSIDE_OBJECT_METAL(10,"金属"),
/**
爆炸物
*/
EM_INSIDE_OBJECT_EXPLOSIVE(11,"爆炸物"),
/**
喷雾喷灌
*/
EM_INSIDE_OBJECT_CONTAINERSPRAY(12,"喷雾喷灌"),
/**
烟花爆竹
*/
EM_INSIDE_OBJECT_EXPLOSIVE_FIREWORKS(13,"烟花爆竹"),
/**
打火机
*/
EM_INSIDE_OBJECT_LIGHTER(14,"打火机"),
/**
警棍
*/
EM_INSIDE_OBJECT_STICK(15,"警棍"),
/**
指虎
*/
EM_INSIDE_OBJECT_BRASSKNUCKLE(16,"指虎"),
/**
手铐
*/
EM_INSIDE_OBJECT_HANDCUFFS(17,"手铐"),
/**
象牙
*/
EM_INSIDE_OBJECT_IVORY(18,"象牙"),
/**
书籍
*/
EM_INSIDE_OBJECT_BOOK(19,"书籍"),
/**
光盘
*/
EM_INSIDE_OBJECT_CD(20,"光盘"),
/**
锤子
*/
EM_INSIDE_OBJECT_HAMMERS(21,"锤子"),
/**
钳子
*/
EM_INSIDE_OBJECT_PLIERS(22,"钳子"),
/**
斧头
*/
EM_INSIDE_OBJECT_AXE(23,"斧头"),
/**
螺丝刀
*/
EM_INSIDE_OBJECT_SCREW_DRIVER(24,"螺丝刀"),
/**
扳手
*/
EM_INSIDE_OBJECT_WRENCH(25,"扳手"),
/**
电击棍
*/
EM_INSIDE_OBJECT_ELECTRIC_SHOCK_STICK(26,"电击棍"),
/**
保温杯
*/
EM_INSIDE_OBJECT_THERMOS(27,"保温杯"),
/**
玻璃杯
*/
EM_INSIDE_OBJECT_GLASS_BOTTLES(28,"玻璃杯"),
/**
塑料瓶
*/
EM_INSIDE_OBJECT_PLASTIC_BOTTLE(29,"塑料瓶"),
/**
打火机油
*/
EM_INSIDE_OBJECT_IGNITION_OIL(30,"打火机油"),
/**
指甲油
*/
EM_INSIDE_OBJECT_NAIL_POLISH(31,"指甲油"),
/**
工具
*/
EM_INSIDE_OBJECT_BLUNT_INSTRUMENT(32,"工具"),
/**
剪刀
*/
EM_INSIDE_OBJECT_SCISSORS(33,"剪刀"),
/**
电子产品
*/
EM_INSIDE_OBJECT_ELECTRONIC(34,"电子产品"),
/**
手枪
*/
EM_INSIDE_OBJECT_PISTOL(35,"手枪"),
/**
折叠刀
*/
EM_INSIDE_OBJECT_FOLDINGKNIFE(36,"折叠刀"),
/**
尖刀
*/
EM_INSIDE_OBJECT_SHARPKNIFE(37,"尖刀"),
/**
菜刀
*/
EM_INSIDE_OBJECT_KITCHENKNIFE(38,"菜刀"),
/**
美工刀
*/
EM_INSIDE_OBJECT_UTILITYKNIFE(39,"美工刀"),
/**
烟花
*/
EM_INSIDE_OBJECT_FIREWORKS(40,"烟花"),
/**
爆竹
*/
EM_INSIDE_OBJECT_FIRECRACKER(41,"爆竹");

private int value;

private String note;

public String getNote() {
        return note;
    }

public int getValue() {
        return value;
    }

EM_INSIDE_OBJECT_TYPE(int givenValue, String note) {
        this.value = givenValue;
        this.note = note;
    }

public static String getNoteByValue(int givenValue) {
        for (EM_INSIDE_OBJECT_TYPE enumType : EM_INSIDE_OBJECT_TYPE.values()) {
            if (givenValue == enumType.getValue()) {
                return enumType.getNote();
            }
        }
        return null;
    }

public static int getValueByNote(String givenNote) {
        for (EM_INSIDE_OBJECT_TYPE enumType : EM_INSIDE_OBJECT_TYPE.values()) {
            if (givenNote.equals(enumType.getNote())) {
                return enumType.getValue();
            }
        }
        return -1;
    }

public static EM_INSIDE_OBJECT_TYPE getEnum(int value) {
        for (EM_INSIDE_OBJECT_TYPE e : EM_INSIDE_OBJECT_TYPE.values()) {
            if (e.getValue() == value)
                return e;
        }
        return EM_INSIDE_OBJECT_TYPE.EM_INSIDE_OBJECT_UNKNOWN;
    }

}