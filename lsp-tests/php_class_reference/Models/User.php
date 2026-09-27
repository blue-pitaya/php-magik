<?php

namespace App\Models;

class User
{
    public static function getAuthOnThrow()
    {
        return new User;
    }
}
